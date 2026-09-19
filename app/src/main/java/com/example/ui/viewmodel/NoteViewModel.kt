package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import com.example.data.repository.NoteRepository
import com.example.service.ocr.OcrManager
import com.example.service.ocr.OcrResult
import com.example.service.analysis.CaptureAnalyzer
import com.example.service.models.ModelManager
import com.example.service.providers.SecureProviderStore
import com.example.service.providers.TranscriptionOrchestrator
import com.example.service.reminders.ReminderScheduler
import com.example.service.speech.OfflineTranscriber
import com.example.service.speech.PlayerState
import com.example.service.speech.SpeechManager
import com.example.service.speech.SpeechState
import com.example.service.url.UrlMetadataFetcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavigationTab(val label: String, val noteType: NoteType?) {
    ALL("All", null),
    VOICE("Voice", NoteType.VOICE),
    TEXT("Text", NoteType.TEXT),
    URL("URLs", NoteType.URL),
    OCR("OCR", NoteType.OCR)
}

data class UiFeedback(
    val message: String,
    val isError: Boolean = false
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    val modelManager = ModelManager(application.applicationContext)
    val modelState = modelManager.state
    val speechManager = SpeechManager(application.applicationContext, modelManager)
    val ocrManager = OcrManager(application.applicationContext, modelManager)
    val providerStore = SecureProviderStore(application.applicationContext)
    private val transcriptionOrchestrator = TranscriptionOrchestrator(
        providerStore,
        OfflineTranscriber(application.applicationContext, modelManager)
    )
    private val modelDownloadJobs = mutableMapOf<String, Job>()

    init {
        val database = AppDatabase.getDatabase(application.applicationContext)
        repository = NoteRepository(database.noteDao())
    }

    val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val followUps: StateFlow<List<NoteEntity>> = repository.getFollowUps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Navigation & Search State
    private val _selectedTab = MutableStateFlow(NavigationTab.ALL)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    // UI Dialog & Sheet States
    private val _isRecordingSheetVisible = MutableStateFlow(false)
    val isRecordingSheetVisible: StateFlow<Boolean> = _isRecordingSheetVisible.asStateFlow()

    private val _isOcrSheetVisible = MutableStateFlow(false)
    val isOcrSheetVisible: StateFlow<Boolean> = _isOcrSheetVisible.asStateFlow()

    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote.asStateFlow()

    private val _isAddEditDialogVisible = MutableStateFlow(false)
    val isAddEditDialogVisible: StateFlow<Boolean> = _isAddEditDialogVisible.asStateFlow()

    private val _activeNoteTypeForCreate = MutableStateFlow(NoteType.TEXT)
    val activeNoteTypeForCreate: StateFlow<NoteType> = _activeNoteTypeForCreate.asStateFlow()
    private val _prefillUrl = MutableStateFlow("")
    val prefillUrl: StateFlow<String> = _prefillUrl.asStateFlow()

    // OCR Processing State
    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing.asStateFlow()

    private val _lastOcrResult = MutableStateFlow<OcrResult?>(null)
    val lastOcrResult: StateFlow<OcrResult?> = _lastOcrResult.asStateFlow()

    // Feedback events (Snackbar / Toasts)
    private val _feedbackEvents = MutableSharedFlow<UiFeedback>()
    val feedbackEvents: SharedFlow<UiFeedback> = _feedbackEvents.asSharedFlow()

    // Notes List with Tab & Search filtering
    @OptIn(ExperimentalCoroutinesApi::class)
    val notesList: StateFlow<List<NoteEntity>> = combine(_selectedTab, _searchQuery) { tab, query ->
        Pair(tab, query)
    }.flatMapLatest { (tab, query) ->
        if (query.isNotBlank()) {
            repository.searchNotes(query.trim())
        } else if (tab.noteType != null) {
            repository.getNotesByType(tab.noteType)
        } else {
            repository.getAllNotes()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pass-through flows from services
    val speechState: StateFlow<SpeechState> = speechManager.speechState
    val playerState: StateFlow<PlayerState> = speechManager.playerState

    // Tab & Search Actions
    fun selectTab(tab: NavigationTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    // Voice Recording Actions
    fun openVoiceRecordingSheet() {
        _isRecordingSheetVisible.value = true
    }

    fun closeVoiceRecordingSheet() {
        if (speechState.value.isRecording) {
            speechManager.cancelListeningAndRecording()
        }
        _isRecordingSheetVisible.value = false
    }

    fun startVoiceRecording(language: String = java.util.Locale.getDefault().toLanguageTag()) {
        speechManager.startListeningAndRecording(language)
    }

    fun stopVoiceRecordingAndSave(title: String) {
        viewModelScope.launch {
            val result = speechManager.stopListeningAndRecording()
            val audioPath = result.recordedAudioPath
            if (audioPath.isNullOrBlank()) {
                emitFeedback(result.errorMessage ?: "Voice recording could not be saved", isError = true)
                return@launch
            }
            val pending = NoteEntity(
                type = NoteType.VOICE,
                title = title.ifBlank { "Voice note" },
                audioFilePath = audioPath,
                durationSeconds = result.durationSeconds,
                transcriptionStatus = "PROCESSING"
            )
            val id = repository.insertNote(pending)
            _isRecordingSheetVisible.value = false
            emitFeedback("Transcribing…")
            transcribeIntoNote(pending.copy(id = id), "automatic")
        }
    }

    fun cancelVoiceRecording() {
        speechManager.cancelListeningAndRecording()
    }

    // Audio Playback Actions
    fun togglePlayAudio(path: String?) {
        if (path.isNullOrBlank()) return
        if (playerState.value.isPlaying && playerState.value.currentPath == path) {
            speechManager.pausePlayback()
        } else if (!playerState.value.isPlaying && playerState.value.currentPath == path) {
            speechManager.resumePlayback()
        } else {
            speechManager.playAudio(path)
        }
    }

    fun stopAudio() {
        speechManager.stopPlayback()
    }

    // OCR Actions
    fun openOcrSheet() {
        _lastOcrResult.value = null
        _isOcrSheetVisible.value = true
    }

    fun closeOcrSheet() {
        _isOcrSheetVisible.value = false
        _lastOcrResult.value = null
    }

    fun processOcrImage(uri: Uri) {
        viewModelScope.launch {
            _isOcrProcessing.value = true
            val result = ocrManager.recognizeTextFromUri(uri)
            _lastOcrResult.value = result
            _isOcrProcessing.value = false
            if (!result.isSuccess) {
                emitFeedback(result.errorMessage ?: "Failed to extract text from image", isError = true)
            }
        }
    }

    fun processOcrBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isOcrProcessing.value = true
            val result = ocrManager.recognizeTextFromBitmap(bitmap)
            _lastOcrResult.value = result
            _isOcrProcessing.value = false
            if (!result.isSuccess) {
                emitFeedback(result.errorMessage ?: "Failed to extract text from image", isError = true)
            }
        }
    }

    fun saveOcrNote(title: String, extractedText: String, imagePath: String?) {
        viewModelScope.launch {
            val result = _lastOcrResult.value
            if (result == null || !result.isSuccess) {
                emitFeedback("OCR quality is too low. Retry with another provider.", isError = true)
                return@launch
            }
            val insights = CaptureAnalyzer.analyze(NoteType.OCR, extractedText, title)
            val note = NoteEntity(
                type = NoteType.OCR,
                title = insights.title,
                content = extractedText,
                summary = insights.summary,
                category = insights.category,
                imageUri = imagePath,
                rawOcrText = result.text,
                normalizedOcrText = extractedText,
                ocrProvider = result.provider,
                ocrModel = result.model,
                detectedLanguages = result.detectedLanguages,
                providerConfidence = result.confidence,
                ocrGeometryJson = result.geometryJson,
                ocrTimestamp = System.currentTimeMillis(),
                ocrStatus = "SUCCESS"
            )
            repository.insertNote(note)
            _isOcrSheetVisible.value = false
            _lastOcrResult.value = null
            emitFeedback("OCR note saved successfully")
        }
    }

    // Note Creation & Editing Dialogs
    fun openCreateNoteDialog(type: NoteType = NoteType.TEXT) {
        _editingNote.value = null
        _activeNoteTypeForCreate.value = type
        if (type != NoteType.URL) _prefillUrl.value = ""
        _isAddEditDialogVisible.value = true
    }

    fun openSharedUrl(url: String) {
        _editingNote.value = null
        _activeNoteTypeForCreate.value = NoteType.URL
        _prefillUrl.value = UrlMetadataFetcher.normalize(url)
        _isAddEditDialogVisible.value = true
    }

    fun openEditNoteDialog(note: NoteEntity) {
        _editingNote.value = note
        _activeNoteTypeForCreate.value = note.type
        _isAddEditDialogVisible.value = true
    }

    fun closeAddEditDialog() {
        _isAddEditDialogVisible.value = false
        _editingNote.value = null
        _prefillUrl.value = ""
    }

    fun saveNote(
        title: String,
        content: String,
        type: NoteType,
        url: String? = null,
        urlDescription: String? = null,
        tags: String = ""
    ) {
        viewModelScope.launch {
            val current = _editingNote.value
            if (current != null) {
                val analysisText = if (type == NoteType.URL) "$urlDescription\n$content" else content
                val insights = CaptureAnalyzer.analyze(type, analysisText, title, current.summary)
                val updated = current.copy(
                    title = insights.title,
                    content = content.trim(),
                    url = if (type == NoteType.URL) url?.trim() else current.url,
                    urlDescription = if (type == NoteType.URL) urlDescription?.trim() else current.urlDescription,
                    tags = tags.trim(),
                    summary = insights.summary,
                    category = insights.category,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updated)
                if (type == NoteType.URL && !url.isNullOrBlank()) viewModelScope.launch { enrichUrlNote(updated) }
                emitFeedback("Note updated")
            } else {
                val normalizedUrl = if (type == NoteType.URL && !url.isNullOrBlank()) UrlMetadataFetcher.normalize(url) else null
                val analysisText = if (type == NoteType.URL) "$urlDescription\n$content" else content
                val insights = CaptureAnalyzer.analyze(type, analysisText, title)
                val newNote = NoteEntity(
                    type = type,
                    title = insights.title,
                    content = content.trim(),
                    url = normalizedUrl,
                    urlDescription = if (type == NoteType.URL) urlDescription?.trim() else null,
                    tags = tags.trim(),
                    summary = insights.summary,
                    category = insights.category,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val id = repository.insertNote(newNote)
                if (type == NoteType.URL && normalizedUrl != null) viewModelScope.launch { enrichUrlNote(newNote.copy(id = id)) }
                emitFeedback("Note created")
            }
            _isAddEditDialogVisible.value = false
            _editingNote.value = null
            _prefillUrl.value = ""
        }
    }

    // Pin / Delete
    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
            emitFeedback(if (!note.isPinned) "Note pinned to top" else "Note unpinned")
        }
    }

    fun toggleImportant(note: NoteEntity) {
        viewModelScope.launch {
            repository.toggleImportant(note.id, note.isImportant)
            emitFeedback(if (!note.isImportant) "Marked important" else "Removed from important")
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            if (playerState.value.currentPath == note.audioFilePath) {
                speechManager.stopPlayback()
            }
            repository.deleteNote(note)
            ReminderScheduler.cancel(getApplication(), note.id)
            note.audioFilePath?.let { runCatching { java.io.File(it).delete() } }
            note.imageUri?.let { runCatching { java.io.File(it).delete() } }
            emitFeedback("Note deleted")
        }
    }

    fun setFollowUp(note: NoteEntity, followUpAt: Long?) {
        viewModelScope.launch {
            repository.setFollowUp(note.id, followUpAt)
            if (followUpAt == null) ReminderScheduler.cancel(getApplication(), note.id)
            else ReminderScheduler.schedule(getApplication(), note.id, note.title, followUpAt)
            emitFeedback(if (followUpAt == null) "Follow-up removed" else "Follow-up scheduled")
        }
    }

    fun toggleFollowUpDone(note: NoteEntity) {
        viewModelScope.launch {
            repository.toggleFollowUpDone(note)
            emitFeedback(if (note.isFollowUpDone) "Follow-up reopened" else "Follow-up completed")
        }
    }

    fun retranscribe(note: NoteEntity, provider: String = "automatic") {
        val path = note.audioFilePath ?: return
        viewModelScope.launch {
            emitFeedback("Transcribing…")
            transcribeIntoNote(note.copy(transcriptionRetryCount = note.transcriptionRetryCount + 1), provider)
        }
    }

    private suspend fun transcribeIntoNote(note: NoteEntity, provider: String) {
        val path = note.audioFilePath ?: return
        repository.updateNote(note.copy(transcriptionStatus = "PROCESSING", transcriptionFailureReason = ""))
        transcriptionOrchestrator.transcribe(path, note.durationSeconds, provider).fold(
            onSuccess = { result ->
                // Semantic intelligence is deliberately downstream of accepted raw speech.
                val insights = CaptureAnalyzer.analyze(NoteType.VOICE, result.rawTranscript, note.title.takeUnless { it == "Voice note" }.orEmpty())
                repository.updateNote(note.copy(
                    title = insights.title,
                    content = result.rawTranscript,
                    rawTranscript = result.rawTranscript,
                    cleanedTranscript = "",
                    summary = insights.summary,
                    category = insights.category,
                    transcriptionProvider = result.provider,
                    transcriptionModel = result.model,
                    detectedLanguage = result.detectedLanguage,
                    transcriptionTimestamp = result.timestamp,
                    transcriptionStatus = "SUCCESS",
                    transcriptionFailureReason = "",
                    updatedAt = System.currentTimeMillis()
                ))
                emitFeedback("Transcript saved • ${result.provider} / ${result.model}")
            },
            onFailure = { error ->
                repository.updateNote(note.copy(
                    content = "",
                    transcriptionStatus = "FAILED",
                    transcriptionFailureReason = error.localizedMessage ?: "Transcription failed",
                    updatedAt = System.currentTimeMillis()
                ))
                emitFeedback("Transcription quality was too low. Retry with another provider.", isError = true)
            }
        )
    }

    private suspend fun enrichUrlNote(note: NoteEntity) {
        val url = note.url ?: return
        UrlMetadataFetcher.fetch(url).onSuccess { metadata ->
            val text = listOf(metadata.description, note.content).filter(String::isNotBlank).joinToString("\n")
            val insights = CaptureAnalyzer.analyze(
                NoteType.URL,
                text,
                preferredTitle = metadata.title.ifBlank { note.title },
                preferredSummary = metadata.description
            )
            repository.updateNote(note.copy(
                title = insights.title,
                summary = insights.summary,
                category = insights.category,
                urlDescription = metadata.siteName.ifBlank { note.urlDescription.orEmpty() },
                updatedAt = System.currentTimeMillis()
            ))
        }.onFailure {
            emitFeedback("Link saved; this site did not expose a preview")
        }
    }

    fun downloadModel(modelId: String) {
        if (modelDownloadJobs[modelId]?.isActive == true) return
        modelDownloadJobs[modelId] = viewModelScope.launch {
            modelManager.download(modelId).fold(
                onSuccess = { emitFeedback("Model downloaded and verified") },
                onFailure = { emitFeedback(it.localizedMessage ?: "Model download failed", isError = true) }
            )
            modelDownloadJobs.remove(modelId)
        }
    }

    fun cancelModelDownload(modelId: String) {
        modelDownloadJobs.remove(modelId)?.cancel()
        emitFeedback("Download paused. You can resume it later.")
    }

    fun activateModel(modelId: String) {
        modelManager.activate(modelId).fold(
            onSuccess = {
                speechManager.reloadTranscriptionModel()
                ocrManager.close()
                emitFeedback("Model activated")
            },
            onFailure = { emitFeedback(it.localizedMessage ?: "Model could not be activated", isError = true) }
        )
    }

    fun deleteModel(modelId: String) {
        modelManager.delete(modelId).fold(
            onSuccess = {
                speechManager.reloadTranscriptionModel()
                ocrManager.close()
                emitFeedback("Downloaded model deleted")
            },
            onFailure = { emitFeedback(it.localizedMessage ?: "Model could not be deleted", isError = true) }
        )
    }

    private fun emitFeedback(message: String, isError: Boolean = false) {
        viewModelScope.launch {
            _feedbackEvents.emit(UiFeedback(message, isError))
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.release()
        ocrManager.close()
    }
}
