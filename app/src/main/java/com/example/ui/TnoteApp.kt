package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import com.example.service.models.DownloadableModel
import com.example.service.models.ModelKind
import com.example.service.models.ModelManagerState
import com.example.ui.components.AddEditNoteDialog
import com.example.ui.components.AudioRecordBottomSheet
import com.example.ui.components.OcrCaptureBottomSheet
import com.example.ui.viewmodel.NoteViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppPage { HOME, CAPTURE, TRANSCRIPTIONS, FOLLOW_UPS, DETAIL, MODELS }

@Composable
fun TnoteApp(viewModel: NoteViewModel) {
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val recordingVisible by viewModel.isRecordingSheetVisible.collectAsStateWithLifecycle()
    val ocrVisible by viewModel.isOcrSheetVisible.collectAsStateWithLifecycle()
    val editorVisible by viewModel.isAddEditDialogVisible.collectAsStateWithLifecycle()
    val editingNote by viewModel.editingNote.collectAsStateWithLifecycle()
    val createType by viewModel.activeNoteTypeForCreate.collectAsStateWithLifecycle()
    val ocrProcessing by viewModel.isOcrProcessing.collectAsStateWithLifecycle()
    val ocrResult by viewModel.lastOcrResult.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()

    var page by remember { mutableStateOf(AppPage.HOME) }
    var selectedNoteId by remember { mutableStateOf<Long?>(null) }
    val selectedNote = notes.firstOrNull { it.id == selectedNoteId }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.feedbackEvents.collectLatest { snackbar.showSnackbar(it.message) }
    }

    fun openDetail(note: NoteEntity) {
        selectedNoteId = note.id
        page = AppPage.DETAIL
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (page != AppPage.DETAIL && page != AppPage.CAPTURE && page != AppPage.MODELS) {
                AppBottomBar(page = page, onSelect = { page = it })
            }
        },
        floatingActionButton = {
            if (page == AppPage.HOME) {
                FloatingActionButton(onClick = { page = AppPage.CAPTURE }) {
                    Icon(Icons.Default.Add, contentDescription = "Capture")
                }
            }
        }
    ) { padding ->
        when (page) {
            AppPage.HOME -> HomePage(notes, ::openDetail, { page = AppPage.MODELS }, Modifier.padding(padding))
            AppPage.CAPTURE -> CapturePage(
                onBack = { page = AppPage.HOME },
                onVoice = viewModel::openVoiceRecordingSheet,
                onText = { viewModel.openCreateNoteDialog(NoteType.TEXT) },
                onLink = { viewModel.openCreateNoteDialog(NoteType.URL) },
                onImage = viewModel::openOcrSheet,
                modifier = Modifier.padding(padding)
            )
            AppPage.TRANSCRIPTIONS -> TranscriptionsPage(
                notes.filter { it.type == NoteType.VOICE },
                ::openDetail,
                Modifier.padding(padding)
            )
            AppPage.FOLLOW_UPS -> FollowUpsPage(
                notes = followUps,
                onOpen = ::openDetail,
                onToggle = viewModel::toggleFollowUpDone,
                modifier = Modifier.padding(padding)
            )
            AppPage.DETAIL -> DetailPage(
                note = selectedNote,
                onBack = { page = AppPage.HOME },
                onEdit = viewModel::openEditNoteDialog,
                onTomorrow = { note ->
                    viewModel.setFollowUp(note, System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
                },
                onRemoveFollowUp = { viewModel.setFollowUp(it, null) },
                onRetranscribe = viewModel::retranscribe,
                modifier = Modifier.padding(padding)
            )
            AppPage.MODELS -> ModelManagerPage(
                state = modelState,
                onBack = { page = AppPage.HOME },
                onDownload = viewModel::downloadModel,
                onCancel = viewModel::cancelModelDownload,
                onActivate = viewModel::activateModel,
                onDelete = viewModel::deleteModel,
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (recordingVisible) {
        AudioRecordBottomSheet(
            speechState = speechState,
            onStartRecording = viewModel::startVoiceRecording,
            onStopAndSave = viewModel::stopVoiceRecordingAndSave,
            onCancel = viewModel::cancelVoiceRecording,
            onDismiss = viewModel::closeVoiceRecordingSheet
        )
    }
    if (ocrVisible) {
        OcrCaptureBottomSheet(
            isProcessing = ocrProcessing,
            lastOcrResult = ocrResult,
            onProcessUri = viewModel::processOcrImage,
            onProcessBitmap = viewModel::processOcrBitmap,
            onSaveOcrNote = viewModel::saveOcrNote,
            onDismiss = viewModel::closeOcrSheet
        )
    }
    if (editorVisible) {
        AddEditNoteDialog(
            note = editingNote,
            initialType = createType,
            onSave = viewModel::saveNote,
            onDismiss = viewModel::closeAddEditDialog
        )
    }
}

@Composable
private fun AppBottomBar(page: AppPage, onSelect: (AppPage) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(
            Triple(AppPage.HOME, "Home", Icons.Default.Home),
            Triple(AppPage.TRANSCRIPTIONS, "Transcripts", Icons.Default.Mic),
            Triple(AppPage.FOLLOW_UPS, "Follow-ups", Icons.Default.NotificationsActive)
        ).forEach { (destination, label, icon) ->
            NavigationBarItem(
                selected = page == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(
    notes: List<NoteEntity>,
    onOpen: (NoteEntity) -> Unit,
    onModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(notes, query) {
        if (query.isBlank()) notes else notes.filter {
            it.title.contains(query, true) || it.content.contains(query, true) ||
                it.tags.contains(query, true) || it.summary.contains(query, true) ||
                it.category.contains(query, true) || it.url.orEmpty().contains(query, true)
        }
    }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Tnote", fontWeight = FontWeight.Bold)
                    Text("Capture it. Find it. Follow it.", style = MaterialTheme.typography.labelMedium)
                }
            },
            actions = {
                IconButton(onClick = onModels) {
                    Icon(Icons.Default.Settings, contentDescription = "Offline models")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search notes and transcripts") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        if (visible.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "Your thoughts start here" else "Nothing found",
                message = if (query.isBlank()) "Tap + to capture voice, text, a link, or an image." else "Try a different search."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { items(visible, key = { it.id }) { NoteCard(it, onOpen) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelManagerPage(
    state: ModelManagerState,
    onBack: () -> Unit,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = { Text("Offline models", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Text(
            "Choose speed or accuracy. Downloads are verified and stored only inside Tnote.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("VOICE TRANSCRIPTION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(state.models.filter { it.kind == ModelKind.VOICE }, key = { it.id }) { model ->
                ModelCard(model, state, onDownload, onCancel, onActivate, onDelete)
            }
            item { Text("IMAGE OCR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
            items(state.models.filter { it.kind == ModelKind.OCR }, key = { it.id }) { model ->
                ModelCard(model, state, onDownload, onCancel, onActivate, onDelete)
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: DownloadableModel,
    state: ModelManagerState,
    onDownload: (String) -> Unit,
    onCancel: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val download = state.downloads[model.id]
    val installed = model.id in state.installedIds
    val active = when (model.kind) {
        ModelKind.VOICE -> state.activeVoiceId == model.id
        ModelKind.OCR -> state.activeOcrId == model.id
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.title, fontWeight = FontWeight.SemiBold)
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (active) "ACTIVE" else model.quality, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatModelBytes(model.totalBytes), style = MaterialTheme.typography.labelMedium)
            if (download?.isDownloading == true) {
                LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${(download.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { onCancel(model.id) }) { Text("Pause") }
                }
            } else {
                download?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !installed -> Button(onClick = { onDownload(model.id) }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(if (download?.error == null) " Download" else " Retry")
                        }
                        !active -> Button(onClick = { onActivate(model.id) }) { Text("Use model") }
                    }
                    if (installed && !model.embedded && !active) {
                        TextButton(onClick = { onDelete(model.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(" Delete")
                        }
                    }
                }
            }
        }
    }
}

private fun formatModelBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "%.0f MB".format(bytes / (1024.0 * 1024))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapturePage(
    onBack: () -> Unit,
    onVoice: () -> Unit,
    onText: () -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = { Text("Capture", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Text(
            "What do you want to remember?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(20.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CaptureAction(Icons.Default.Mic, "Voice", "Record audio with a live transcript", onVoice)
            CaptureAction(Icons.Default.Description, "Text", "Write a clear note", onText)
            CaptureAction(Icons.Default.Link, "Link", "Save a URL with context", onLink)
            CaptureAction(Icons.Default.CameraAlt, "Image & OCR", "Pick or photograph text", onImage)
        }
    }
}

@Composable
private fun CaptureAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(14.dp).size(24.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptionsPage(notes: List<NoteEntity>, onOpen: (NoteEntity) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Transcriptions", fontWeight = FontWeight.Bold) })
        if (notes.isEmpty()) EmptyState("No transcripts yet", "Capture a voice note and its transcript will appear here.")
        else LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { items(notes, key = { it.id }) { NoteCard(it, onOpen) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowUpsPage(
    notes: List<NoteEntity>,
    onOpen: (NoteEntity) -> Unit,
    onToggle: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Follow-ups", fontWeight = FontWeight.Bold) })
        if (notes.isEmpty()) EmptyState("Nothing waiting", "Open any note and schedule a follow-up for tomorrow.")
        else LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(if (note.isFollowUpDone) .58f else 1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggle(note) }) {
                            Icon(
                                if (note.isFollowUpDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (note.isFollowUpDone) "Reopen" else "Complete",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(Modifier.weight(1f).clickable { onOpen(note) }) {
                            Text(note.title, fontWeight = FontWeight.SemiBold)
                            Text(formatDate(note.followUpAt), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(
    note: NoteEntity?,
    onBack: () -> Unit,
    onEdit: (NoteEntity) -> Unit,
    onTomorrow: (NoteEntity) -> Unit,
    onRemoveFollowUp: (NoteEntity) -> Unit,
    onRetranscribe: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (note == null) {
        EmptyState("Note unavailable", "It may have been deleted.")
        return
    }
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = { Text(note.type.label) },
            actions = { TextButton(onClick = { onEdit(note) }) { Text("Edit") } }
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(note.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(formatDate(note.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            note.imageUri?.let { path ->
                item {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Captured image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
            }
            if (note.content.isNotBlank()) item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(note.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(18.dp))
                }
            }
            if (note.type == NoteType.VOICE && note.audioFilePath != null) item {
                Button(onClick = { onRetranscribe(note) }) {
                    Icon(Icons.Default.Mic, null)
                    Text("  Transcribe offline")
                }
            }
            note.url?.takeIf { it.isNotBlank() }?.let { url ->
                item { Button(onClick = { uriHandler.openUri(url) }) { Icon(Icons.Default.Link, null); Text("  Open link") } }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Follow-up", Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.SemiBold)
                    if (note.followUpAt == null) TextButton(onClick = { onTomorrow(note) }) { Text("Tomorrow") }
                    else TextButton(onClick = { onRemoveFollowUp(note) }) { Text("Remove") }
                }
                if (note.followUpAt != null) Text(formatDate(note.followUpAt), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onOpen: (NoteEntity) -> Unit) {
    val icon = when (note.type) {
        NoteType.VOICE -> Icons.Default.Mic
        NoteType.TEXT -> Icons.Default.Description
        NoteType.URL -> Icons.Default.Link
        NoteType.OCR -> Icons.Default.Image
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(note) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp).size(20.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (note.followUpAt != null) Icon(Icons.Default.MoreTime, "Follow-up", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                val preview = note.content.ifBlank { note.urlDescription ?: note.url.orEmpty() }
                if (preview.isNotBlank()) Text(
                    preview,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(formatDate(note.updatedAt), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Description, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun formatDate(value: Long?): String {
    if (value == null) return ""
    return SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(value))
}
