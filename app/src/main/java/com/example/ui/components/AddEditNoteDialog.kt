package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteDialog(
    note: NoteEntity?,
    initialType: NoteType,
    prefillUrl: String = "",
    onSave: (title: String, content: String, type: NoteType, url: String?, urlDescription: String?, tags: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEditing = note != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf(note?.type ?: initialType) }
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var url by remember(note?.id, prefillUrl) { mutableStateOf(note?.url ?: prefillUrl) }
    var urlDescription by remember { mutableStateOf(note?.urlDescription ?: "") }
    var tags by remember { mutableStateOf(note?.tags ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.testTag("add_edit_note_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit ${selectedType.label}" else "New ${selectedType.label}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_add_edit_sheet_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Note Type Selector (only shown if creating new note and not OCR/Voice which have dedicated sheets)
            if (!isEditing && selectedType != NoteType.VOICE && selectedType != NoteType.OCR) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedType == NoteType.TEXT,
                        onClick = { selectedType = NoteType.TEXT },
                        label = { Text("Text Note") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        modifier = Modifier.testTag("chip_select_type_text")
                    )
                    FilterChip(
                        selected = selectedType == NoteType.URL,
                        onClick = { selectedType = NoteType.URL },
                        label = { Text("URL Link") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        modifier = Modifier.testTag("chip_select_type_url")
                    )
                }
            }

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("Enter a title...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_input_title"),
                shape = RoundedCornerShape(12.dp)
            )

            // URL Fields (if type is URL)
            if (selectedType == NoteType.URL) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clipText.isNullOrBlank()) {
                                    url = clipText.trim()
                                }
                            },
                            modifier = Modifier.testTag("paste_url_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input_url"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = urlDescription,
                    onValueChange = { urlDescription = it },
                    label = { Text("URL Summary / Description (Optional)") },
                    placeholder = { Text("Brief description of the link...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input_url_description"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Main Content Field (for text note, or optional notes on URL/Voice/OCR)
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = {
                    Text(
                        when (selectedType) {
                            NoteType.URL -> "Additional Notes (Optional)"
                            NoteType.VOICE -> "Transcription Text"
                            NoteType.OCR -> "OCR Extracted Text"
                            NoteType.TEXT -> "Note Content"
                        }
                    )
                },
                placeholder = { Text("Type your note content here...") },
                minLines = if (selectedType == NoteType.TEXT) 6 else 3,
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_input_content"),
                shape = RoundedCornerShape(12.dp)
            )

            // Tags Field
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (Optional, comma-separated)") },
                placeholder = { Text("e.g. Work, Ideas, Recipe") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_input_tags"),
                shape = RoundedCornerShape(12.dp)
            )

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_add_edit_button")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSave(title, content, selectedType, url, urlDescription, tags)
                    },
                    enabled = title.isNotBlank() || content.isNotBlank() || url.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_add_edit_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isEditing) "Update" else "Save")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
