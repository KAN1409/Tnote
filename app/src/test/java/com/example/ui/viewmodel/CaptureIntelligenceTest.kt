package com.example.ui.viewmodel

import com.example.data.model.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIntelligenceTest {
    @Test fun bilingualVoiceGetsUsefulTitleAndTags() {
        val r = CaptureIntelligence.analyze("فكرة للـ project الجديد نراجع design مع العميل بكرة", NoteType.VOICE)
        assertTrue(r.title.startsWith("فكرة"))
        assertTrue(r.tags.contains("work"))
        assertTrue(r.tags.contains("arabic-english"))
    }

    @Test fun reminderLanguageIsCategorizedAsTask() {
        val r = CaptureIntelligence.analyze("افتكر أعمل follow up بكرة", NoteType.TEXT)
        assertEquals("task", r.category)
    }

    @Test fun urlUsesHostWhenNoBodyExists() {
        val r = CaptureIntelligence.analyze("", NoteType.URL, "https://www.youtube.com/watch?v=abc")
        assertEquals("Saved from youtube.com", r.title)
        assertEquals("reference", r.category)
    }
}
