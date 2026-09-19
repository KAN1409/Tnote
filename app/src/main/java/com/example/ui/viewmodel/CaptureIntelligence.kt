package com.example.ui.viewmodel

import com.example.data.model.NoteType
import java.net.URI
import java.util.Locale

data class CaptureInsight(
    val title: String,
    val category: String,
    val summary: String,
    val tags: String
)

/**
 * Lightweight, deterministic capture understanding that works fully offline.
 * It deliberately does not pretend to be an LLM: it derives a useful title,
 * category and short summary from the text already produced by transcription/OCR.
 */
object CaptureIntelligence {
    private val arabic = Regex("[\\u0600-\\u06FF]")
    private val sentenceBreak = Regex("[.!?؟\\n]+")
    private val whitespace = Regex("\\s+")

    fun analyze(rawText: String, type: NoteType, url: String? = null): CaptureInsight {
        val text = rawText.replace(whitespace, " ").trim()
        val category = categoryFor(text, type, url)
        val summary = summarize(text)
        val title = titleFor(text, type, url)
        val languageTag = when {
            text.isBlank() -> "unknown-language"
            arabic.containsMatchIn(text) && Regex("[A-Za-z]").containsMatchIn(text) -> "arabic-english"
            arabic.containsMatchIn(text) -> "arabic"
            else -> "english"
        }
        return CaptureInsight(title, category, summary, listOf(category, languageTag).distinct().joinToString(","))
    }

    private fun titleFor(text: String, type: NoteType, url: String?): String {
        if (type == NoteType.URL && !url.isNullOrBlank()) {
            runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return "Saved from $it"
            }
        }
        if (text.isBlank()) return when (type) {
            NoteType.VOICE -> "Voice note"
            NoteType.OCR -> "Scanned text"
            NoteType.URL -> "Saved link"
            NoteType.TEXT -> "Note"
        }
        val first = text.split(sentenceBreak).firstOrNull { it.isNotBlank() } ?: text
        val words = first.trim().split(" ").filter { it.isNotBlank() }
        return words.take(9).joinToString(" ").trim().take(72).ifBlank { "Note" }
    }

    private fun summarize(text: String): String {
        if (text.isBlank()) return ""
        val sentences = text.split(sentenceBreak).map { it.trim() }.filter { it.isNotBlank() }
        return sentences.take(2).joinToString(". ").take(240)
    }

    private fun categoryFor(text: String, type: NoteType, url: String?): String {
        val normalized = text.lowercase(Locale.ROOT)
        val groups = linkedMapOf(
            "work" to listOf("meeting","project","client","design","drawing","site","quotation","invoice","اجتماع","مشروع","عميل","تصميم","موقع","عرض سعر"),
            "task" to listOf("todo","to do","remember","remind","deadline","follow up","لازم","افتكر","ذكرني","متابعة","مطلوب"),
            "idea" to listOf("idea","concept","maybe","what if","فكرة","اقتراح","ممكن"),
            "health" to listOf("doctor","hospital","medicine","appointment","دكتور","مستشفى","دواء","ميعاد","تحليل"),
            "reference" to listOf("article","research","reference","guide","مقال","بحث","مرجع","شرح")
        )
        groups.entries.firstOrNull { (_, keys) -> keys.any { normalized.contains(it) } }?.let { return it.key }
        if (type == NoteType.URL || !url.isNullOrBlank()) return "reference"
        return when (type) {
            NoteType.VOICE -> "voice"
            NoteType.OCR -> "document"
            NoteType.URL -> "reference"
            NoteType.TEXT -> "note"
        }
    }
}
