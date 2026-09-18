package com.example.service.speech

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Locale

data class SpeechState(
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
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

class SpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _speechState = MutableStateFlow(SpeechState())
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var timerJob: Job? = null
    private var playerTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentOutputFile: File? = null

    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListeningAndRecording(language: String = Locale.getDefault().toLanguageTag()) {
        stopPlayback()
        _speechState.value = SpeechState(
            isRecording = true,
            isListening = true,
            transcribedText = "",
            partialText = "",
            rmsLevel = 0f,
            durationSeconds = 0,
            errorMessage = null
        )

        // 1. Prepare Audio File & MediaRecorder
        try {
            val audioDir = File(context.filesDir, "audio_notes").apply { mkdirs() }
            val audioFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = audioFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // If MediaRecorder fails (e.g. concurrent MIC capture or device limitation), continue with SpeechRecognizer
            currentOutputFile = null
        }

        // 2. Start Duration Timer
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0
            while (isActive && _speechState.value.isRecording) {
                delay(1000)
                seconds++
                _speechState.value = _speechState.value.copy(durationSeconds = seconds)
            }
        }

        // 3. Start SpeechRecognizer
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _speechState.value = _speechState.value.copy(isListening = true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _speechState.value = _speechState.value.copy(
                            rmsLevel = (rmsdB.coerceIn(0f, 10f) / 10f)
                        )
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for online speech recognition"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error ($error)"
                        }
                        // Only set error message if we have not recorded any text
                        if (_speechState.value.transcribedText.isBlank() && _speechState.value.partialText.isBlank()) {
                            _speechState.value = _speechState.value.copy(
                                errorMessage = message,
                                isListening = false
                            )
                        } else {
                            _speechState.value = _speechState.value.copy(isListening = false)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        val currentText = _speechState.value.transcribedText
                        val combined = if (currentText.isBlank()) text else "$currentText $text"
                        _speechState.value = _speechState.value.copy(
                            transcribedText = combined.trim(),
                            partialText = "",
                            isListening = false
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            _speechState.value = _speechState.value.copy(partialText = partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _speechState.value = _speechState.value.copy(
                errorMessage = "Failed to start speech recognizer: ${e.localizedMessage}",
                isListening = false
            )
        }
    }

    fun stopListeningAndRecording(): SpeechState {
        timerJob?.cancel()

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // If stopped prematurely
        }
        mediaRecorder = null

        val finalTranscribed = if (_speechState.value.transcribedText.isNotBlank()) {
            if (_speechState.value.partialText.isNotBlank() && !_speechState.value.transcribedText.contains(_speechState.value.partialText)) {
                "${_speechState.value.transcribedText} ${_speechState.value.partialText}".trim()
            } else {
                _speechState.value.transcribedText
            }
        } else {
            _speechState.value.partialText
        }

        val resultState = _speechState.value.copy(
            isRecording = false,
            isListening = false,
            transcribedText = finalTranscribed,
            partialText = "",
            recordedAudioPath = currentOutputFile?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
        )
        _speechState.value = resultState
        return resultState
    }

    fun cancelListeningAndRecording() {
        timerJob?.cancel()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {}
        mediaRecorder = null

        currentOutputFile?.let { file ->
            if (file.exists()) file.delete()
        }
        currentOutputFile = null

        _speechState.value = SpeechState()
    }

    // Audio Playback
    fun playAudio(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        if (_playerState.value.isPlaying && _playerState.value.currentPath == filePath) {
            pausePlayback()
            return
        }

        stopPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPlayback()
                }
            }

            val duration = mediaPlayer?.duration ?: 0
            _playerState.value = PlayerState(
                isPlaying = true,
                currentPath = filePath,
                currentPositionMs = 0,
                totalDurationMs = duration
            )

            startPlayerProgressTimer()
        } catch (e: IOException) {
            stopPlayback()
        }
    }

    private fun startPlayerProgressTimer() {
        playerTimerJob?.cancel()
        playerTimerJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition ?: 0
                val total = mediaPlayer?.duration ?: 0
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = current,
                    totalDurationMs = total
                )
                delay(200)
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
                startPlayerProgressTimer()
            }
        }
    }

    fun stopPlayback() {
        playerTimerJob?.cancel()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {}
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    fun release() {
        cancelListeningAndRecording()
        stopPlayback()
    }
}
