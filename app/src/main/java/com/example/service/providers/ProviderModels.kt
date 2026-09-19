package com.example.service.providers

enum class ProcessingMode { AUTOMATIC, CLOUD_ONLY, OFFLINE_ONLY }

enum class ProviderFailureKind { AUTH, QUOTA, RATE_LIMIT, TIMEOUT, NETWORK, UNAVAILABLE, MALFORMED, QUALITY, PERMANENT }

data class ProviderFailure(
    val kind: ProviderFailureKind,
    val message: String,
    val retryAfterSeconds: Long? = null,
    val httpCode: Int? = null
) : Exception(message)

data class TranscriptionResult(
    val rawTranscript: String,
    val provider: String,
    val model: String,
    val detectedLanguage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

interface TranscriptionProvider {
    val id: String
    val model: String
    suspend fun transcribe(path: String, vocabulary: List<String>): Result<TranscriptionResult>
}
