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
import com.example.service.speech.PlayerState
import com.example.service.speech.SpeechManager
import com.example.service.speech.SpeechState
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val speechManager = SpeechManager(application.applicationContext)
    val ocrManager = OcrManager(application.applicationContext)

    init {
        val database = AppDatabase.getDatabase(application.applicationContext)
        repository = NoteRepository(database.noteDao())
    }

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
        val result = speechManager.stopListeningAndRecording()
        val text = result.transcribedText.ifBlank { "Voice recording transcription" }
        val noteTitle = title.ifBlank { "Voice Note - ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" }

        viewModelScope.launch {
            val note = NoteEntity(
                type = NoteType.VOICE,
                title = noteTitle,
                content = text,
                audioFilePath = result.recordedAudioPath,
                durationSeconds = result.durationSeconds
            )
            repository.insertNote(note)
            _isRecordingSheetVisible.value = false
            emitFeedback("Voice note saved successfully")
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
            val noteTitle = title.ifBlank { "OCR Note - ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" }
            val note = NoteEntity(
                type = NoteType.OCR,
                title = noteTitle,
                content = extractedText,
                imageUri = imagePath
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
                val updated = current.copy(
                    title = title.trim().ifBlank { "Untitled Note" },
                    content = content.trim(),
                    url = if (type == NoteType.URL) url?.trim() else current.url,
                    urlDescription = if (type == NoteType.URL) urlDescription?.trim() else current.urlDescription,
                    tags = tags.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updated)
                emitFeedback("Note updated")
            } else {
                val newNote = NoteEntity(
                    type = type,
                    title = title.trim().ifBlank {
                        when (type) {
                            NoteType.URL -> "Saved Link"
                            NoteType.VOICE -> "Voice Note"
                            NoteType.OCR -> "Image OCR"
                            NoteType.TEXT -> "Untitled Note"
                        }
                    },
                    content = content.trim(),
                    url = if (type == NoteType.URL) url?.trim() else null,
                    urlDescription = if (type == NoteType.URL) urlDescription?.trim() else null,
                    tags = tags.trim(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertNote(newNote)
                emitFeedback("Note created")
            }
            _isAddEditDialogVisible.value = false
            _editingNote.value = null
        }
    }

    // Pin / Delete
    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
            emitFeedback(if (!note.isPinned) "Note pinned to top" else "Note unpinned")
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            if (playerState.value.currentPath == note.audioFilePath) {
                speechManager.stopPlayback()
            }
            repository.deleteNote(note)
            emitFeedback("Note deleted")
        }
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
