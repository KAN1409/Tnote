package com.example.service.analysis

import com.example.data.model.NoteType

data class CaptureInsights(
    val title: String,
    val summary: String,
    val category: String
)

/** Fast, private, deterministic capture organization that works without a network or AI account. */
object CaptureAnalyzer {
    private val categories = linkedMapOf(
        "Work" to listOf("project", "client", "meeting", "contract", "invoice", "github", "build", "apk", "مشروع", "عميل", "اجتماع", "مقاول", "شغل"),
        "Ideas" to listOf("idea", "concept", "design", "inspiration", "فكرة", "تصميم", "اقتراح", "إلهام"),
        "Tasks" to listOf("todo", "must", "need to", "remember", "follow up", "لازم", "محتاج", "افتكر", "متابعة"),
        "Health" to listOf("doctor", "medicine", "hospital", "test result", "دكتور", "دواء", "مستشفى", "تحليل", "أشعة"),
        "Learning" to listOf("learn", "tutorial", "course", "research", "اختبار", "شرح", "تعلم", "بحث", "ترجمة"),
        "Personal" to listOf("family", "home", "birthday", "ناصر", "البيت", "العائلة", "شخصي")
    )

    fun analyze(
        type: NoteType,
        content: String,
        preferredTitle: String? = null,
        preferredSummary: String? = null
    ): CaptureInsights {
        val clean = content.replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        val category = classify(clean)
        val title = preferredTitle?.trim()?.takeIf { it.isMeaningfulTitle() }
            ?: deriveTitle(clean, type, category)
        val summary = preferredSummary?.trim()?.takeIf(String::isNotBlank)
            ?: summarize(clean)
        return CaptureInsights(title, summary, category)
    }

    private fun classify(text: String): String {
        val normalized = text.lowercase()
        return categories.maxByOrNull { (_, words) -> words.count(normalized::contains) }
            ?.takeIf { (_, words) -> words.any(normalized::contains) }
            ?.key ?: "General"
    }

    private fun deriveTitle(text: String, type: NoteType, category: String): String {
        val first = text.lineSequence()
            .flatMap { it.split(Regex("(?<=[.!؟])\\s+")).asSequence() }
            .map(String::trim)
            .firstOrNull { it.count(Char::isLetterOrDigit) >= 4 }
            .orEmpty()
        if (first.isNotBlank()) return first.cleanTitle().limit(64)
        return when (type) {
            NoteType.VOICE -> "$category voice note"
            NoteType.OCR -> "$category image"
            NoteType.URL -> "$category link"
            NoteType.TEXT -> "$category note"
        }
    }

    private fun summarize(text: String): String {
        if (text.isBlank()) return ""
        val sentences = text.replace('\n', ' ')
            .split(Regex("(?<=[.!؟])\\s+"))
            .map(String::trim)
            .filter { it.isNotBlank() }
        val selected = if (sentences.size <= 2) sentences else sentences.take(3)
        return selected.joinToString(" ").limit(360)
    }

    private fun String.cleanTitle(): String = trim()
        .removePrefix("- ")
        .removePrefix("• ")
        .trim(' ', '.', '،', ',', ':', ';')

    private fun String.limit(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

    private fun String.isMeaningfulTitle(): Boolean {
        if (isBlank()) return false
        val lower = lowercase()
        return lower !in setOf("untitled note", "saved link", "voice note", "image ocr") &&
            !lower.startsWith("voice note -") && !lower.startsWith("ocr note -")
    }
}
