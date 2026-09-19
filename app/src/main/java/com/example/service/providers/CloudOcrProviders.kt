package com.example.service.providers

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CloudOcrResult(
    val rawText: String,
    val provider: String,
    val model: String,
    val detectedLanguages: String = "",
    val confidence: Float? = null,
    val geometryJson: String = ""
)

class CloudOcrOrchestrator(private val store: SecureProviderStore) {
    suspend fun recognize(bitmap: Bitmap): Result<CloudOcrResult> = withContext(Dispatchers.IO) {
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val errors = mutableListOf<String>()
        if (store.mode("ocr") != ProcessingMode.OFFLINE_ONLY) {
            store.get(SecureProviderStore.GOOGLE_VISION_KEY)?.let { key ->
                google(bytes, key).fold({ if (quality(it.rawText)) return@withContext Result.success(it) else errors += "Google Vision quality rejected" }, { errors += it.message.orEmpty() })
            }
            val azureKey = store.get(SecureProviderStore.AZURE_VISION_KEY)
            val endpoint = store.get(SecureProviderStore.AZURE_VISION_ENDPOINT)
            if (!azureKey.isNullOrBlank() && !endpoint.isNullOrBlank()) {
                azure(bytes, azureKey, endpoint).fold({ if (quality(it.rawText)) return@withContext Result.success(it) else errors += "Azure quality rejected" }, { errors += it.message.orEmpty() })
            }
            store.get(SecureProviderStore.OCR_SPACE_KEY)?.let { key ->
                ocrSpace(bytes, key).fold({ if (quality(it.rawText)) return@withContext Result.success(it) else errors += "OCR.space quality rejected" }, { errors += it.message.orEmpty() })
            }
        }
        Result.failure(ProviderFailure(ProviderFailureKind.UNAVAILABLE, errors.joinToString("; ").ifBlank { "No cloud OCR provider is configured." }))
    }

    private fun google(bytes: ByteArray, key: String): Result<CloudOcrResult> = runCatching {
        val body = JSONObject().put("requests", JSONArray().put(JSONObject()
            .put("image", JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            .put("features", JSONArray().put(JSONObject().put("type", "DOCUMENT_TEXT_DETECTION")))))
        val json = JSONObject(post("https://vision.googleapis.com/v1/images:annotate?key=$key", body.toString().toByteArray(), mapOf("Content-Type" to "application/json")))
        val response = json.getJSONArray("responses").getJSONObject(0)
        response.optJSONObject("error")?.let { throw ProviderFailure(ProviderFailureKind.PERMANENT, it.optString("message")) }
        val annotation = response.optJSONObject("fullTextAnnotation") ?: throw ProviderFailure(ProviderFailureKind.MALFORMED, "Google Vision detected no text.")
        CloudOcrResult(annotation.optString("text").trim(), "Google Vision", "DOCUMENT_TEXT_DETECTION", geometryJson = annotation.toString())
    }

    private fun azure(bytes: ByteArray, key: String, endpoint: String): Result<CloudOcrResult> = runCatching {
        val url = endpoint.trimEnd('/') + "/computervision/imageanalysis:analyze?api-version=2024-02-01&features=read"
        val json = JSONObject(post(url, bytes, mapOf("Ocp-Apim-Subscription-Key" to key, "Content-Type" to "application/octet-stream")))
        val read = json.optJSONObject("readResult") ?: throw ProviderFailure(ProviderFailureKind.MALFORMED, "Azure detected no text.")
        val lines = mutableListOf<String>()
        val blocks = read.optJSONArray("blocks") ?: JSONArray()
        for (i in 0 until blocks.length()) {
            val blockLines = blocks.getJSONObject(i).optJSONArray("lines") ?: continue
            for (j in 0 until blockLines.length()) lines += blockLines.getJSONObject(j).optString("text")
        }
        CloudOcrResult(lines.joinToString("\n").trim(), "Azure Vision", "Image Analysis Read 4.0", geometryJson = read.toString())
    }

    private fun ocrSpace(bytes: ByteArray, key: String): Result<CloudOcrResult> = runCatching {
        val body = "apikey=${java.net.URLEncoder.encode(key, "UTF-8")}&OCREngine=3&isOverlayRequired=true&base64Image=${java.net.URLEncoder.encode("data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP), "UTF-8")}".toByteArray()
        val json = JSONObject(post("https://api.ocr.space/parse/image", body, mapOf("Content-Type" to "application/x-www-form-urlencoded")))
        if (json.optBoolean("IsErroredOnProcessing")) throw ProviderFailure(ProviderFailureKind.PERMANENT, json.optJSONArray("ErrorMessage")?.optString(0) ?: "OCR.space failed")
        val parsed = json.optJSONArray("ParsedResults")?.optJSONObject(0) ?: throw ProviderFailure(ProviderFailureKind.MALFORMED, "OCR.space detected no text.")
        CloudOcrResult(parsed.optString("ParsedText").trim(), "OCR.space", "Engine 3", geometryJson = parsed.optJSONObject("TextOverlay")?.toString().orEmpty())
    }

    private fun quality(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 2) return false
        val useful = clean.count { it.isLetterOrDigit() }
        val repeated = Regex("(.{3,20})(?:\\s*\\1){3,}", RegexOption.IGNORE_CASE).containsMatchIn(clean)
        return useful >= 2 && useful.toDouble() / clean.length >= .35 && !repeated
    }

    private fun post(url: String, body: ByteArray, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 90_000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw ProviderFailure(when (code) { 429 -> ProviderFailureKind.RATE_LIMIT; in 500..599 -> ProviderFailureKind.UNAVAILABLE; 401,403 -> ProviderFailureKind.AUTH; else -> ProviderFailureKind.PERMANENT }, "OCR provider HTTP $code: ${text.take(200)}", c.getHeaderField("Retry-After")?.toLongOrNull(), code)
        return text
    }
}
