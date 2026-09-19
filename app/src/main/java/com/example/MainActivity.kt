package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TnoteApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoteViewModel
import com.example.service.reminder.FollowUpReminderWorker

class MainActivity : ComponentActivity() {
    private val requestedNoteId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedNoteId.value = intent?.getLongExtra(FollowUpReminderWorker.EXTRA_NOTE_ID, -1L)?.takeIf { it > 0 }
        setContent {
            MyApplicationTheme {
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val viewModel: NoteViewModel = viewModel()
                TnoteApp(viewModel = viewModel, initialNoteId = requestedNoteId.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedNoteId.value = intent.getLongExtra(FollowUpReminderWorker.EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
    }
}
