package com.example.service.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import com.example.service.models.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Whisper Tiny multilingual INT8 running fully on-device. Audio and transcripts never leave the phone. */
class OfflineTranscriber(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var recognizerModelName: String? = null

    suspend fun transcribe(wavPath: String): Result<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            runCatching {
                val wave = WaveReader.readWave(wavPath)
                require(wave.samples.isNotEmpty()) { "The recording is empty." }
                val selectedModel = modelManager.activeVoiceName()
                if (recognizerModelName != selectedModel) close()
                val engine = recognizer ?: createRecognizer().also {
                    recognizer = it
                    recognizerModelName = selectedModel
                }
                val stream = engine.createStream()
                try {
                    stream.acceptWaveform(wave.samples, wave.sampleRate)
                    engine.decode(stream)
                    engine.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }.also { require(it.isNotBlank()) { "No speech could be recognized." } }
            }
        }
    }

    private fun createRecognizer(): OfflineRecognizer {
        val downloaded = modelManager.activeVoiceFiles()
        val (encoder, decoder, tokens) = if (downloaded != null) {
            Triple(downloaded.encoder, downloaded.decoder, downloaded.tokens)
        } else {
            val modelDir = File(context.filesDir, "whisper-tiny").apply { mkdirs() }
            val embeddedEncoder = materializeModel(modelDir, "tiny-encoder.int8.onnx", 12_937_772L)
            val embeddedDecoder = materializeModel(modelDir, "tiny-decoder.int8.onnx", 89_855_401L)
            val embeddedTokens = File(modelDir, "tiny-tokens.txt").also { target ->
                if (!target.exists() || target.length() != 816_730L) {
                    context.assets.open("models/tiny-tokens.txt").use { input ->
                        FileOutputStream(target).use(input::copyTo)
                    }
                }
            }
            Triple(embeddedEncoder, embeddedDecoder, embeddedTokens)
        }
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                language = "",
                task = "transcribe",
                tailPaddings = 1000
            ),
            tokens = tokens.absolutePath,
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
            debug = false
        )
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = model
            )
        )
    }

    private fun materializeModel(directory: File, name: String, expectedBytes: Long): File {
        val target = File(directory, name)
        if (target.exists() && target.length() == expectedBytes) return target
        val partNames = context.assets.list("models").orEmpty()
            .filter { it.startsWith("$name.part.") }
            .sorted()
        require(partNames.isNotEmpty()) { "Offline model $name is missing from the application." }
        val partial = File(directory, "$name.partial")
        FileOutputStream(partial).use { output ->
            partNames.forEach { part ->
                context.assets.open("models/$part").use { it.copyTo(output) }
            }
        }
        require(partial.length() == expectedBytes) { "Offline model $name is incomplete." }
        if (target.exists()) {
            check(target.delete()) { "The previous offline model $name could not be replaced." }
        }
        check(partial.renameTo(target)) { "Offline model $name could not be prepared." }
        return target
    }

    fun close() {
        recognizer?.release()
        recognizer = null
        recognizerModelName = null
    }
}
