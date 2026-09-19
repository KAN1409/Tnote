package com.example.service.providers

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

private const val CONNECT_TIMEOUT = 15_000
private const val READ_TIMEOUT = 90_000

class GeminiTranscriptionProvider(private val apiKey: () -> String?) : TranscriptionProvider {
    override val id = "Gemini"
    // Current model documented by Google's official audio guide; intentionally not the nonexistent gemini-3.5-transcribe.
    override val model = "gemini-3.8-flash"

    override suspend fun transcribe(path: String, vocabulary: List<String>) = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey().orEmpty().takeIf(String::isNotBlank)
                ?: throw ProviderFailure(ProviderFailureKind.AUTH, "Gemini API key is not configured.")
            val file = File(path)
            if (file.length() > 14_000_000) throw ProviderFailure(ProviderFailureKind.PERMANENT, "Recording is too large for inline Gemini upload.")
            val prompt = """Transcribe only the actual spoken speech faithfully. The speaker may code-switch naturally between Egyptian Arabic and English. Do not translate, summarize, rewrite, complete missing speech, add timestamps, or identify speakers. Preserve English technical terms. If speech is unclear, omit it rather than invent it. Vocabulary: ${vocabulary.joinToString(", ")}. Return transcript text only."""
            val body = JSONObject().apply {
                put("model", model)
                put("input", JSONArray().put(JSONObject().put("type", "text").put("text", prompt)).put(
                    JSONObject().put("type", "audio")
                        .put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                        .put("mime_type", "audio/wav")
                ))
            }
            val response = request("https://generativelanguage.googleapis.com/v1beta/interactions", "POST",
                mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"), body.toString().toByteArray())
            val json = JSONObject(response)
            val text = json.optString("output_text").ifBlank {
                json.optJSONObject("interaction")?.optString("output_text").orEmpty()
            }.trim()
            if (text.isBlank()) throw ProviderFailure(ProviderFailureKind.MALFORMED, "Gemini returned no transcript.")
            TranscriptionResult(text, id, model)
        }
    }
}

class GroqTranscriptionProvider(private val apiKey: () -> String?) : TranscriptionProvider {
    override val id = "Groq"
    override val model = "whisper-large-v3"

    override suspend fun transcribe(path: String, vocabulary: List<String>) = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey().orEmpty().takeIf(String::isNotBlank)
                ?: throw ProviderFailure(ProviderFailureKind.AUTH, "Groq API key is not configured.")
            val file = File(path)
            val boundary = "Tnote-${UUID.randomUUID()}"
            val out = java.io.ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", model); field("response_format", "verbose_json"); field("temperature", "0")
            field("prompt", "Egyptian Arabic and English technical speech. Spell: ${vocabulary.joinToString(", ")}")
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
            val response = request("https://api.groq.com/openai/v1/audio/transcriptions", "POST",
                mapOf("Authorization" to "Bearer $key", "Content-Type" to "multipart/form-data; boundary=$boundary"), out.toByteArray())
            val json = JSONObject(response)
            val text = json.optString("text").trim()
            if (text.isBlank()) throw ProviderFailure(ProviderFailureKind.MALFORMED, "Groq returned no transcript.")
            TranscriptionResult(text, id, model, json.optString("language"))
        }
    }
}

private fun request(url: String, method: String, headers: Map<String, String>, body: ByteArray): String {
    try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = CONNECT_TIMEOUT; readTimeout = READ_TIMEOUT; doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val kind = when (code) { 401, 403 -> ProviderFailureKind.AUTH; 429 -> ProviderFailureKind.RATE_LIMIT; in 500..599 -> ProviderFailureKind.UNAVAILABLE; else -> ProviderFailureKind.PERMANENT }
            throw ProviderFailure(kind, "Provider HTTP $code${if (text.isBlank()) "" else ": ${text.take(240)}"}", connection.getHeaderField("Retry-After")?.toLongOrNull(), code)
        }
        return text
    } catch (e: SocketTimeoutException) { throw ProviderFailure(ProviderFailureKind.TIMEOUT, "Provider timed out.") }
    catch (e: ProviderFailure) { throw e }
    catch (e: Exception) { throw ProviderFailure(ProviderFailureKind.NETWORK, e.localizedMessage ?: "Network provider failed.") }
}
