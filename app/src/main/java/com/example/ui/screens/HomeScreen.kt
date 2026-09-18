package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import com.example.ui.components.AddEditNoteDialog
import com.example.ui.components.AudioRecordBottomSheet
import com.example.ui.components.NoteItemCard
import com.example.ui.components.OcrCaptureBottomSheet
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.viewmodel.NavigationTab
import com.example.ui.viewmodel.NoteViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NoteViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notesList.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val isRecordingSheetVisible by viewModel.isRecordingSheetVisible.collectAsStateWithLifecycle()
    val isOcrSheetVisible by viewModel.isOcrSheetVisible.collectAsStateWithLifecycle()
    val isAddEditDialogVisible by viewModel.isAddEditDialogVisible.collectAsStateWithLifecycle()
    val activeNoteTypeForCreate by viewModel.activeNoteTypeForCreate.collectAsStateWithLifecycle()
    val editingNote by viewModel.editingNote.collectAsStateWithLifecycle()

    val isOcrProcessing by viewModel.isOcrProcessing.collectAsStateWithLifecycle()
    val lastOcrResult by viewModel.lastOcrResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    // Listen for feedback events (Snackbars)
    LaunchedEffect(Unit) {
        viewModel.feedbackEvents.collectLatest { feedback ->
            snackbarHostState.showSnackbar(feedback.message)
        }
    }

    // Tab Note Counts
    val noteCounts = remember(notes) {
        mapOf(
            NavigationTab.ALL to notes.size,
            NavigationTab.VOICE to notes.count { it.type == NoteType.VOICE },
            NavigationTab.TEXT to notes.count { it.type == NoteType.TEXT },
            NavigationTab.URL to notes.count { it.type == NoteType.URL },
            NavigationTab.OCR to notes.count { it.type == NoteType.OCR }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Voice Notes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openVoiceRecordingSheet() },
                        modifier = Modifier.testTag("top_app_bar_voice_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Quick Voice Record",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openOcrSheet() },
                        modifier = Modifier.testTag("top_app_bar_ocr_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Quick Image OCR",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val icon = when (tab) {
                        NavigationTab.ALL -> Icons.Default.FilterList
                        NavigationTab.VOICE -> Icons.Default.Mic
                        NavigationTab.TEXT -> Icons.Default.Description
                        NavigationTab.URL -> Icons.Default.Link
                        NavigationTab.OCR -> Icons.Default.DocumentScanner
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(imageVector = icon, contentDescription = tab.label) },
                        label = { Text(text = tab.label) },
                        modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Expanded Speed Dial Options
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Voice Note Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Voice to Text",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabMenuExpanded = false
                                    viewModel.openVoiceRecordingSheet()
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.testTag("fab_voice_note")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Note")
                            }
                        }

                        // OCR Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Scan Image (OCR)",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabMenuExpanded = false
                                    viewModel.openOcrSheet()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("fab_ocr_note")
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = "OCR Note")
                            }
                        }

                        // URL Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Save URL Link",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabMenuExpanded = false
                                    viewModel.openCreateNoteDialog(NoteType.URL)
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.testTag("fab_url_note")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = "Save URL")
                            }
                        }

                        // Text Note Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Text Note",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabMenuExpanded = false
                                    viewModel.openCreateNoteDialog(NoteType.TEXT)
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.testTag("fab_text_note")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Text Note")
                            }
                        }
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = {
                        isFabMenuExpanded = !isFabMenuExpanded
                    },
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("main_fab_button")
                ) {
                    Icon(
                        imageVector = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isFabMenuExpanded) "Close actions" else "Add note",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search and Filter Bar
            SearchAndFilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) },
                noteCounts = noteCounts
            )

            // Notes List or Empty State
            if (notes.isEmpty()) {
                EmptyNotesView(
                    selectedTab = selectedTab,
                    searchQuery = searchQuery,
                    onVoiceClick = { viewModel.openVoiceRecordingSheet() },
                    onTextClick = { viewModel.openCreateNoteDialog(NoteType.TEXT) },
                    onUrlClick = { viewModel.openCreateNoteDialog(NoteType.URL) },
                    onOcrClick = { viewModel.openOcrSheet() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_lazy_column"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = notes,
                        key = { it.id }
                    ) { note ->
                        NoteItemCard(
                            note = note,
                            playerState = playerState,
                            onTogglePlayAudio = { path -> viewModel.togglePlayAudio(path) },
                            onEditClick = { viewModel.openEditNoteDialog(it) },
                            onDeleteClick = { noteToDelete = it },
                            onTogglePin = { viewModel.togglePin(it) }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete \"${note.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note)
                        noteToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { noteToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("delete_note_dialog")
        )
    }

    // Bottom Sheets & Dialogs
    if (isRecordingSheetVisible) {
        AudioRecordBottomSheet(
            speechState = speechState,
            onStartRecording = { viewModel.startVoiceRecording() },
            onStopAndSave = { title -> viewModel.stopVoiceRecordingAndSave(title) },
            onCancel = { viewModel.cancelVoiceRecording() },
            onDismiss = { viewModel.closeVoiceRecordingSheet() }
        )
    }

    if (isOcrSheetVisible) {
        OcrCaptureBottomSheet(
            isProcessing = isOcrProcessing,
            lastOcrResult = lastOcrResult,
            onProcessUri = { uri -> viewModel.processOcrImage(uri) },
            onProcessBitmap = { bmp -> viewModel.processOcrBitmap(bmp) },
            onSaveOcrNote = { title, text, imgPath -> viewModel.saveOcrNote(title, text, imgPath) },
            onDismiss = { viewModel.closeOcrSheet() }
        )
    }

    if (isAddEditDialogVisible) {
        AddEditNoteDialog(
            note = editingNote,
            initialType = activeNoteTypeForCreate,
            onSave = { title, content, type, url, urlDesc, tags ->
                viewModel.saveNote(title, content, type, url, urlDesc, tags)
            },
            onDismiss = { viewModel.closeAddEditDialog() }
        )
    }
}

@Composable
private fun EmptyNotesView(
    selectedTab: NavigationTab,
    searchQuery: String,
    onVoiceClick: () -> Unit,
    onTextClick: () -> Unit,
    onUrlClick: () -> Unit,
    onOcrClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (icon, title, subtitle) = if (searchQuery.isNotBlank()) {
            Triple(
                Icons.Default.FilterList,
                "No notes found",
                "No results matching \"$searchQuery\". Try searching with different keywords."
            )
        } else when (selectedTab) {
            NavigationTab.ALL -> Triple(
                Icons.Default.NoteAdd,
                "Your notes notebook is empty",
                "Record your thoughts with voice transcription, scan text from images with OCR, save web links, or create text notes."
            )
            NavigationTab.VOICE -> Triple(
                Icons.Default.Mic,
                "No voice notes yet",
                "Tap the microphone button to record audio with real-time speech-to-text transcription."
            )
            NavigationTab.TEXT -> Triple(
                Icons.Default.Description,
                "No text notes yet",
                "Create and organize plain text notes with tags and titles."
            )
            NavigationTab.URL -> Triple(
                Icons.Default.Link,
                "No saved URLs yet",
                "Save bookmarks and web links with custom summaries and descriptions."
            )
            NavigationTab.OCR -> Triple(
                Icons.Default.DocumentScanner,
                "No OCR notes yet",
                "Take a photo or choose an image from your gallery to extract text with ML Kit."
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (searchQuery.isBlank()) {
            when (selectedTab) {
                NavigationTab.ALL, NavigationTab.VOICE -> {
                    OutlinedButton(
                        onClick = onVoiceClick,
                        modifier = Modifier.testTag("empty_state_voice_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Voice Note")
                    }
                }
                NavigationTab.TEXT -> {
                    OutlinedButton(
                        onClick = onTextClick,
                        modifier = Modifier.testTag("empty_state_text_button")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Text Note")
                    }
                }
                NavigationTab.URL -> {
                    OutlinedButton(
                        onClick = onUrlClick,
                        modifier = Modifier.testTag("empty_state_url_button")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save URL Link")
                    }
                }
                NavigationTab.OCR -> {
                    OutlinedButton(
                        onClick = onOcrClick,
                        modifier = Modifier.testTag("empty_state_ocr_button")
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Image (OCR)")
                    }
                }
            }
        }
    }
}
