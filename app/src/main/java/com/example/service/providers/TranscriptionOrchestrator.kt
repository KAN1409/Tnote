package com.example.service.providers

import com.example.service.speech.OfflineTranscriber

class TranscriptionOrchestrator(
    private val store: SecureProviderStore,
    private val offline: OfflineTranscriber
) {
    private val vocabulary = listOf("transcription", "OCR", "PaddleOCR", "application", "Android", "API", "Gemini", "Whisper", "Groq", "Tnote")
    private val providers by lazy { listOf<TranscriptionProvider>(
        GeminiTranscriptionProvider { store.get(SecureProviderStore.GEMINI_KEY) },
        GroqTranscriptionProvider { store.get(SecureProviderStore.GROQ_KEY) }
    ) }

    suspend fun transcribe(path: String, durationSeconds: Int, requested: String = "automatic"): Result<TranscriptionResult> {
        val mode = store.mode("transcription")
        val cloud = when (requested.lowercase()) {
            "gemini" -> providers.filter { it.id == "Gemini" }
            "groq" -> providers.filter { it.id == "Groq" }
            "offline" -> emptyList()
            else -> providers
        }
        val failures = mutableListOf<String>()
        if (mode != ProcessingMode.OFFLINE_ONLY && requested.lowercase() != "offline") {
            for (provider in cloud) {
                if (provider.id == "Gemini" && !store.has(SecureProviderStore.GEMINI_KEY)) continue
                if (provider.id == "Groq" && !store.has(SecureProviderStore.GROQ_KEY)) continue
                val result = provider.transcribe(path, vocabulary)
                if (result.isSuccess) {
                    val value = result.getOrThrow()
                    val verdict = TranscriptQualityGate.validate(value.rawTranscript, durationSeconds)
                    if (verdict.accepted) return Result.success(value)
                    failures += "${provider.id}: ${verdict.reason}"
                    continue
                }
                val error = result.exceptionOrNull()
                failures += "${provider.id}: ${error?.message}"
                // Deterministic failover only. Auth/permanent errors also move to the next configured provider.
            }
        }
        if (mode == ProcessingMode.CLOUD_ONLY && requested.lowercase() != "offline")
            return Result.failure(ProviderFailure(ProviderFailureKind.UNAVAILABLE, failures.joinToString("; ").ifBlank { "No cloud provider is configured." }))
        return offline.transcribe(path).fold(
            onSuccess = { text ->
                val verdict = TranscriptQualityGate.validate(text, durationSeconds)
                if (verdict.accepted) Result.success(TranscriptionResult(text, "Offline", "Whisper ${if (text.isNotBlank()) "active" else ""}".trim()))
                else Result.failure(ProviderFailure(ProviderFailureKind.QUALITY, verdict.reason ?: "Transcription quality was too low."))
            },
            onFailure = { Result.failure(ProviderFailure(ProviderFailureKind.UNAVAILABLE, (failures + (it.message ?: "Offline failed")).joinToString("; "))) }
        )
    }
}
