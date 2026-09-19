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
import kotlin.math.sqrt

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
                val segments = splitOnSilence(wave.samples, wave.sampleRate)
                require(segments.isNotEmpty()) { "No clear speech was detected in the recording." }
                segments.mapNotNull { samples ->
                    val stream = engine.createStream()
                    try {
                        stream.acceptWaveform(samples, wave.sampleRate)
                        engine.decode(stream)
                        engine.getResult(stream).text.trim().takeIf(String::isNotBlank)
                    } finally {
                        stream.release()
                    }
                }.filterNot(::isSuspiciousSegment).joinToString(" ").trim().also { transcript ->
                    require(transcript.isNotBlank()) { "No speech could be recognized." }
                    require(!isRepetitionHallucination(transcript)) {
                        "Whisper produced a repeated-word hallucination. Try again closer to the microphone."
                    }
                }
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
                tailPaddings = 300
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

    /** Trims silence and creates separate language-detection opportunities around real pauses. */
    internal fun splitOnSilence(samples: FloatArray, sampleRate: Int): List<FloatArray> {
        if (samples.isEmpty() || sampleRate <= 0) return emptyList()
        val frameSize = (sampleRate / 50).coerceAtLeast(1) // 20 ms
        val frameRms = samples.asList().chunked(frameSize).map { frame ->
            sqrt(frame.sumOf { it.toDouble() * it } / frame.size.coerceAtLeast(1)).toFloat()
        }
        val peak = frameRms.maxOrNull() ?: 0f
        val sortedRms = frameRms.sorted()
        val noiseFloor = sortedRms[(sortedRms.lastIndex * 0.20f).toInt().coerceIn(0, sortedRms.lastIndex)]
        val threshold = maxOf(0.0045f, noiseFloor * 3.0f, peak * 0.055f)
        if (peak < threshold || peak < noiseFloor * 1.8f) return emptyList()
        val maxSilentFrames = 30 // 600 ms: avoid chopping natural Arabic/English pauses
        val paddingFrames = 12 // 240 ms preserves word edges
        val minimumSpeechFrames = 15 // 300 ms
        val ranges = mutableListOf<IntRange>()
        var startFrame = -1
        var lastSpeechFrame = -1
        frameRms.forEachIndexed { index, rms ->
            if (rms >= threshold) {
                if (startFrame < 0) startFrame = index
                lastSpeechFrame = index
            } else if (startFrame >= 0 && index - lastSpeechFrame > maxSilentFrames) {
                if (lastSpeechFrame - startFrame + 1 >= minimumSpeechFrames) {
                    ranges += (startFrame - paddingFrames).coerceAtLeast(0)..(lastSpeechFrame + paddingFrames).coerceAtMost(frameRms.lastIndex)
                }
                startFrame = -1
                lastSpeechFrame = -1
            }
        }
        if (startFrame >= 0 && lastSpeechFrame - startFrame + 1 >= minimumSpeechFrames) {
            ranges += (startFrame - paddingFrames).coerceAtLeast(0)..(lastSpeechFrame + paddingFrames).coerceAtMost(frameRms.lastIndex)
        }
        return ranges.map { range ->
            samples.copyOfRange(
                (range.first * frameSize).coerceAtMost(samples.size),
                ((range.last + 1) * frameSize).coerceAtMost(samples.size)
            )
        }.filter { it.isNotEmpty() }
    }

    internal fun isSuspiciousSegment(text: String): Boolean {
        val normalized = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        if (normalized.isBlank()) return true
        val words = normalized.split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.size >= 8) {
            val repeatedBigrams = words.zipWithNext().groupingBy { it }.eachCount().values.maxOrNull() ?: 0
            if (repeatedBigrams >= 4) return true
        }
        val scriptRuns = Regex("[\\p{IsLatin}]+|[\\p{InArabic}]+").findAll(text).map { it.value }.toList()
        val tinyForeignRuns = scriptRuns.count { run -> run.length <= 2 }
        return scriptRuns.size >= 12 && tinyForeignRuns.toFloat() / scriptRuns.size > 0.65f
    }

    internal fun isRepetitionHallucination(text: String): Boolean {
        val words = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (words.size < 6) return false
        val mostFrequent = words.groupingBy { it }.eachCount().maxOfOrNull { it.value } ?: 0
        return mostFrequent.toFloat() / words.size >= 0.6f
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

    fun activeModelName(): String = modelManager.activeVoiceName()

    fun close() {
        recognizer?.release()
        recognizer = null
        recognizerModelName = null
    }
}
