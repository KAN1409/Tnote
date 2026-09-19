package com.example.service.providers

object TranscriptQualityGate {
    data class Verdict(val accepted: Boolean, val reason: String? = null)

    fun validate(text: String, durationSeconds: Int): Verdict {
        val clean = text.trim()
        if (durationSeconds < 1) return Verdict(false, "Recording contains too little speech.")
        if (clean.isBlank()) return Verdict(false, "No speech was recognized.")
        val words = clean.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val plausibleMax = (durationSeconds.coerceAtLeast(1) * 5 + 20)
        if (words.size > plausibleMax) return Verdict(false, "Transcript is implausibly long for this recording.")
        if (words.size >= 6) {
            val topWord = words.groupingBy { it }.eachCount().maxOfOrNull { it.value } ?: 0
            if (topWord.toDouble() / words.size > .55) return Verdict(false, "Excessive repeated speech was detected.")
            for (n in 2..4) {
                val grams = words.windowed(n).map { it.joinToString(" ") }
                val top = grams.groupingBy { it }.eachCount().maxOfOrNull { it.value } ?: 0
                if (top >= 4 && top * n >= words.size / 2) return Verdict(false, "Repeated phrase hallucination was detected.")
            }
        }
        val garbage = clean.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ".,!?؛،:'\"()-_/" }
        if (garbage > clean.length / 8) return Verdict(false, "Transcript contains excessive invalid symbols.")
        return Verdict(true)
    }
}
