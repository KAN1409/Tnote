package com.example.service.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.example.service.models.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.sqrt

data class SpeechState(
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcribedText: String = "",
    val partialText: String = "",
    val rmsLevel: Float = 0f,
    val durationSeconds: Int = 0,
    val recordedAudioPath: String? = null,
    val errorMessage: String? = null
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPath: String? = null,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0
)

/** Records clean 16 kHz PCM and runs Whisper after capture, avoiding microphone contention. */
class SpeechManager(
    private val context: Context,
    modelManager: ModelManager
) {
    private val sampleRate = 16_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val transcriber = OfflineTranscriber(context, modelManager)
    private var recorder: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playerTimerJob: Job? = null

    private val _speechState = MutableStateFlow(SpeechState())
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun isSpeechRecognitionAvailable(): Boolean = true

    fun reloadTranscriptionModel() {
        transcriber.close()
    }

    @Suppress("MissingPermission")
    fun startListeningAndRecording(language: String = "ar") {
        if (_speechState.value.isRecording || _speechState.value.isTranscribing) return
        stopPlayback()
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            _speechState.value = SpeechState(errorMessage = "This device could not initialize audio recording.")
            return
        }
        val file = File(File(context.filesDir, "audio_notes").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.wav")
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (error: Exception) {
            _speechState.value = SpeechState(errorMessage = error.localizedMessage ?: "Microphone could not be opened.")
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            _speechState.value = SpeechState(errorMessage = "Microphone could not be initialized.")
            return
        }
        recorder = audioRecord
        outputFile = file
        _speechState.value = SpeechState(isRecording = true, partialText = "Recording locally…")
        audioRecord.startRecording()
        recordingJob = scope.launch(Dispatchers.IO) { recordToWave(audioRecord, file, minBuffer * 2) }
    }

    suspend fun stopListeningAndRecording(): SpeechState {
        if (!_speechState.value.isRecording) return _speechState.value
        val file = outputFile
        recorder?.let { runCatching { it.stop() } }
        recordingJob?.join()
        recorder?.release()
        recorder = null
        recordingJob = null
        if (file == null || !file.exists() || file.length() <= 44) {
            return SpeechState(errorMessage = "No usable audio was recorded.").also { _speechState.value = it }
        }
        _speechState.value = _speechState.value.copy(
            isRecording = false,
            isTranscribing = true,
            partialText = "Whisper Base is transcribing offline…",
            recordedAudioPath = file.absolutePath
        )
        val result = transcriber.transcribe(file.absolutePath)
        return result.fold(
            onSuccess = { text ->
                _speechState.value.copy(
                    isTranscribing = false,
                    transcribedText = text,
                    partialText = "",
                    errorMessage = null
                )
            },
            onFailure = { error ->
                _speechState.value.copy(
                    isTranscribing = false,
                    partialText = "",
                    errorMessage = error.localizedMessage ?: "Offline transcription failed."
                )
            }
        ).also { _speechState.value = it }
    }

    suspend fun transcribeFile(path: String): Result<String> = transcriber.transcribe(path)

    private suspend fun recordToWave(audioRecord: AudioRecord, file: File, bufferBytes: Int) {
        val samples = ShortArray((bufferBytes / 2).coerceAtLeast(1024))
        var totalSamples = 0L
        RandomAccessFile(file, "rw").use { wav ->
            wav.setLength(0)
            wav.write(ByteArray(44))
            while (currentCoroutineContext().isActive && audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = audioRecord.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                var squareSum = 0.0
                for (index in 0 until count) {
                    val value = samples[index].toInt()
                    wav.write(value and 0xff)
                    wav.write((value shr 8) and 0xff)
                    squareSum += value.toDouble() * value
                }
                totalSamples += count
                val rms = (sqrt(squareSum / count) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                _speechState.value = _speechState.value.copy(
                    rmsLevel = rms,
                    durationSeconds = (totalSamples / sampleRate).toInt()
                )
            }
            writeWaveHeader(wav, totalSamples * 2)
        }
    }

    private fun writeWaveHeader(file: RandomAccessFile, audioBytes: Long) {
        file.seek(0)
        fun ascii(value: String) = file.write(value.toByteArray(Charsets.US_ASCII))
        fun littleInt(value: Long) {
            repeat(4) { file.write(((value shr (8 * it)) and 0xff).toInt()) }
        }
        fun littleShort(value: Int) {
            file.write(value and 0xff)
            file.write((value shr 8) and 0xff)
        }
        ascii("RIFF"); littleInt(audioBytes + 36); ascii("WAVE")
        ascii("fmt "); littleInt(16); littleShort(1); littleShort(1)
        littleInt(sampleRate.toLong()); littleInt((sampleRate * 2).toLong())
        littleShort(2); littleShort(16); ascii("data"); littleInt(audioBytes)
    }

    fun cancelListeningAndRecording() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        recordingJob?.cancel()
        recordingJob = null
        outputFile?.delete()
        outputFile = null
        _speechState.value = SpeechState()
    }

    fun playAudio(filePath: String) {
        if (!File(filePath).exists()) return
        if (_playerState.value.isPlaying && _playerState.value.currentPath == filePath) return pausePlayback()
        if (!_playerState.value.isPlaying && _playerState.value.currentPath == filePath) return resumePlayback()
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener { stopPlayback() }
            }
            _playerState.value = PlayerState(true, filePath, 0, mediaPlayer?.duration ?: 0)
            startPlayerProgressTimer()
        } catch (_: IOException) {
            stopPlayback()
        }
    }

    private fun startPlayerProgressTimer() {
        playerTimerJob?.cancel()
        playerTimerJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = mediaPlayer?.currentPosition ?: 0,
                    totalDurationMs = mediaPlayer?.duration ?: 0
                )
                delay(200)
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun resumePlayback() {
        mediaPlayer?.takeIf { !it.isPlaying }?.start()
        _playerState.value = _playerState.value.copy(isPlaying = true)
        startPlayerProgressTimer()
    }

    fun stopPlayback() {
        playerTimerJob?.cancel()
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    fun release() {
        cancelListeningAndRecording()
        stopPlayback()
        transcriber.close()
        scope.cancel()
    }
}
