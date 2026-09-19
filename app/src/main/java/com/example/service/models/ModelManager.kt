package com.example.service.models

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class ModelKind { VOICE, OCR }

data class ModelFile(
    val name: String,
    val url: String,
    val bytes: Long,
    val sha256: String
)

data class DownloadableModel(
    val id: String,
    val kind: ModelKind,
    val title: String,
    val description: String,
    val quality: String,
    val totalBytes: Long,
    val embedded: Boolean,
    val files: List<ModelFile> = emptyList()
)

data class ModelDownloadState(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val isDownloading: Boolean = false,
    val error: String? = null
) {
    val progress: Float get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

data class ModelManagerState(
    val models: List<DownloadableModel>,
    val activeVoiceId: String,
    val activeOcrId: String,
    val installedIds: Set<String>,
    val downloads: Map<String, ModelDownloadState> = emptyMap()
)

data class VoiceModelFiles(val encoder: File, val decoder: File, val tokens: File)
data class OcrModelFiles(val dataDirectory: File)
data class PaddleOcrModelFiles(val detector: File, val recognizer: File, val recognitionConfig: File)

class ModelManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("offline_models", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "downloaded_models").apply { mkdirs() }

    val catalog = listOf(
        DownloadableModel(
            id = VOICE_TINY,
            kind = ModelKind.VOICE,
            title = "Whisper Tiny",
            description = "Built in • Arabic + English • fastest",
            quality = "Standard",
            totalBytes = 103_609_903L,
            embedded = true
        ),
        DownloadableModel(
            id = VOICE_BASE,
            kind = ModelKind.VOICE,
            title = "Whisper Base",
            description = "Arabic + English • more accurate • slower",
            quality = "Better",
            totalBytes = 160_609_290L,
            embedded = false,
            files = listOf(
                ModelFile("encoder.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx", 29_120_534L, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
                ModelFile("decoder.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx", 130_672_026L, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
                ModelFile("tokens.txt", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt", 816_730L, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
            )
        ),
        DownloadableModel(
            id = VOICE_SMALL,
            kind = ModelKind.VOICE,
            title = "Whisper Small",
            description = "Arabic + English • high accuracy • slower",
            quality = "High",
            totalBytes = 375_485_327L,
            embedded = false,
            files = listOf(
                ModelFile("encoder.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-encoder.int8.onnx", 112_442_483L, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
                ModelFile("decoder.onnx", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx", 262_226_114L, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
                ModelFile("tokens.txt", "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt", 816_730L, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
            )
        ),
        DownloadableModel(
            id = OCR_FAST,
            kind = ModelKind.OCR,
            title = "Arabic + English Fast OCR",
            description = "Built in • quick screenshot reading",
            quality = "Standard",
            totalBytes = 5_500_000L,
            embedded = true
        ),
        DownloadableModel(
            id = OCR_BEST,
            kind = ModelKind.OCR,
            title = "Arabic + English Best OCR",
            description = "Highest Tesseract accuracy • slower",
            quality = "Best",
            totalBytes = 28_004_325L,
            embedded = false,
            files = listOf(
                ModelFile("tessdata/ara.traineddata", "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/e12c65a915945e4c28e237a9b52bc4a8f39a0cec/ara.traineddata", 12_603_724L, "ab9d157d8e38ca00e7e39c7d5363a5239e053f5b0dbdb3167dde9d8124335896"),
                ModelFile("tessdata/eng.traineddata", "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/e12c65a915945e4c28e237a9b52bc4a8f39a0cec/eng.traineddata", 15_400_601L, "8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba")
            )
        ),
        DownloadableModel(
            id = OCR_PADDLE_ARABIC,
            kind = ModelKind.OCR,
            title = "PaddleOCR Arabic + English",
            description = "Neural OCR • mixed Arabic + English • best for screenshots",
            quality = "Recommended",
            totalBytes = 12_832_533L,
            embedded = false,
            files = listOf(
                ModelFile("detector.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx", 4_826_518L, "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"),
                ModelFile("detector.yml", "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.yml", 903L, "98069072e1b6b37d727fd9d9f11725faa46d6ea0de012f2ed26caea011c37699"),
                ModelFile("recognizer.onnx", "https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx", 7_998_947L, "799113ebf267fbe742deb99eb36e8d42c9ddc5291ceacf92add41b4d52a59110"),
                ModelFile("recognizer.yml", "https://huggingface.co/PaddlePaddle/arabic_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml", 6_165L, "21368419e6c016c31db55d316d59e11c128e1913e6e6fe10287084710043d3a6")
            )
        )
    )

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<ModelManagerState> = _state.asStateFlow()

    fun activate(modelId: String): Result<Unit> = runCatching {
        val model = catalog.firstOrNull { it.id == modelId } ?: error("Unknown model")
        check(model.embedded || isInstalled(model)) { "Download this model first." }
        val key = if (model.kind == ModelKind.VOICE) ACTIVE_VOICE else ACTIVE_OCR
        preferences.edit().putString(key, model.id).apply()
        refresh()
    }

    suspend fun download(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val model = catalog.firstOrNull { it.id == modelId } ?: error("Unknown model")
            require(!model.embedded) { "This model is already built in." }
            val required = model.totalBytes + 64L * 1024 * 1024
            check(StatFs(root.absolutePath).availableBytes >= required) { "Not enough free storage for this model." }
            val staging = File(root, ".${model.id}.download").apply { mkdirs() }
            _state.value = _state.value.copy(downloads = _state.value.downloads + (model.id to ModelDownloadState(0, model.totalBytes, true)))
            var completed = 0L
            model.files.forEach { file ->
                coroutineContext.ensureActive()
                downloadFile(file, File(staging, file.name), model, completed)
                completed += file.bytes
            }
            val destination = File(root, model.id)
            if (destination.exists()) destination.deleteRecursively()
            check(staging.renameTo(destination)) { "Could not install the verified model." }
            refresh(downloads = _state.value.downloads - model.id)
        }.onFailure { error ->
            if (error is CancellationException) {
                _state.value = _state.value.copy(
                    downloads = _state.value.downloads + (modelId to ModelDownloadState(error = "Download paused"))
                )
                throw error
            }
            _state.value = _state.value.copy(
                downloads = _state.value.downloads + (modelId to ModelDownloadState(error = error.localizedMessage ?: "Download failed"))
            )
        }
    }

    fun delete(modelId: String): Result<Unit> = runCatching {
        val model = catalog.firstOrNull { it.id == modelId } ?: error("Unknown model")
        require(!model.embedded) { "Built-in models cannot be deleted." }
        if (activeId(model.kind) == model.id) {
            val fallback = if (model.kind == ModelKind.VOICE) VOICE_TINY else OCR_FAST
            activate(fallback).getOrThrow()
        }
        File(root, model.id).deleteRecursively()
        File(root, ".${model.id}.download").deleteRecursively()
        refresh(downloads = _state.value.downloads - model.id)
    }

    fun activeVoiceFiles(): VoiceModelFiles? {
        val active = activeId(ModelKind.VOICE)
        if (active == VOICE_TINY) return null
        val directory = File(root, active)
        val files = VoiceModelFiles(File(directory, "encoder.onnx"), File(directory, "decoder.onnx"), File(directory, "tokens.txt"))
        return files.takeIf { it.encoder.isFile && it.decoder.isFile && it.tokens.isFile }
    }

    fun activeOcrFiles(): OcrModelFiles? {
        if (activeId(ModelKind.OCR) != OCR_BEST) return null
        val directory = File(root, OCR_BEST)
        return OcrModelFiles(directory).takeIf {
            File(directory, "tessdata/ara.traineddata").isFile && File(directory, "tessdata/eng.traineddata").isFile
        }
    }

    fun activePaddleOcrFiles(): PaddleOcrModelFiles? {
        if (activeId(ModelKind.OCR) != OCR_PADDLE_ARABIC) return null
        val directory = File(root, OCR_PADDLE_ARABIC)
        return PaddleOcrModelFiles(
            detector = File(directory, "detector.onnx"),
            recognizer = File(directory, "recognizer.onnx"),
            recognitionConfig = File(directory, "recognizer.yml")
        ).takeIf { it.detector.isFile && it.recognizer.isFile && it.recognitionConfig.isFile }
    }

    fun activeVoiceName(): String = catalog.first { it.id == activeId(ModelKind.VOICE) }.title
    fun activeOcrName(): String = catalog.first { it.id == activeId(ModelKind.OCR) }.title

    private suspend fun downloadFile(file: ModelFile, target: File, model: DownloadableModel, completedBefore: Long) {
        target.parentFile?.mkdirs()
        if (target.exists() && target.length() == file.bytes && sha256(target) == file.sha256) return
        if (target.length() > file.bytes) target.delete()
        var existing = target.takeIf(File::exists)?.length() ?: 0L
        val connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Server returned ${connection.responseCode}." }
            val append = existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append) existing = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var current = existing
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        current += count
                        _state.value = _state.value.copy(
                            downloads = _state.value.downloads + (model.id to ModelDownloadState(completedBefore + current, model.totalBytes, true))
                        )
                    }
                }
            }
            check(target.length() == file.bytes) { "${file.name} is incomplete." }
            check(sha256(target) == file.sha256) { "${file.name} failed security verification." }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun activeId(kind: ModelKind): String = when (kind) {
        ModelKind.VOICE -> preferences.getString(ACTIVE_VOICE, VOICE_TINY) ?: VOICE_TINY
        ModelKind.OCR -> preferences.getString(ACTIVE_OCR, OCR_FAST) ?: OCR_FAST
    }

    private fun isInstalled(model: DownloadableModel): Boolean = model.embedded || model.files.all { spec ->
        File(File(root, model.id), spec.name).let { it.exists() && it.length() == spec.bytes }
    }

    private fun readState(downloads: Map<String, ModelDownloadState> = emptyMap()) = ModelManagerState(
        models = catalog,
        activeVoiceId = activeId(ModelKind.VOICE),
        activeOcrId = activeId(ModelKind.OCR),
        installedIds = catalog.filter(::isInstalled).mapTo(mutableSetOf()) { it.id },
        downloads = downloads
    )

    private fun refresh(downloads: Map<String, ModelDownloadState> = _state.value.downloads) {
        _state.value = readState(downloads)
    }

    companion object {
        const val VOICE_TINY = "whisper_tiny"
        const val VOICE_BASE = "whisper_base"
        const val VOICE_SMALL = "whisper_small"
        const val OCR_FAST = "ocr_fast"
        const val OCR_BEST = "ocr_best"
        const val OCR_PADDLE_ARABIC = "ocr_paddle_arabic_v5"
        private const val ACTIVE_VOICE = "active_voice"
        private const val ACTIVE_OCR = "active_ocr"
    }
}
