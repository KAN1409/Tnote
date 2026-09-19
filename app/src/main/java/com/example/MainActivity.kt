package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TnoteApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoteViewModel

class MainActivity : ComponentActivity() {
    private var sharedUrl by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: NoteViewModel = viewModel()
                TnoteApp(viewModel = viewModel, sharedUrl = sharedUrl)
            }
        }
        sharedUrl = extractSharedUrl(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl = extractSharedUrl(intent)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        return Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')')
    }
}
