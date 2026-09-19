package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: NoteType = NoteType.TEXT,
    val title: String,
    val content: String = "",
    val url: String? = null,
    val urlDescription: String? = null,
    val audioFilePath: String? = null,
    val durationSeconds: Int = 0,
    val imageUri: String? = null,
    val isPinned: Boolean = false,
    val isImportant: Boolean = false,
    val summary: String = "",
    val category: String = "General",
    val rawTranscript: String = "",
    val cleanedTranscript: String = "",
    val transcriptionProvider: String = "",
    val transcriptionModel: String = "",
    val detectedLanguage: String = "",
    val transcriptionTimestamp: Long? = null,
    val transcriptionStatus: String = "",
    val transcriptionFailureReason: String = "",
    val transcriptionRetryCount: Int = 0,
    val rawOcrText: String = "",
    val normalizedOcrText: String = "",
    val ocrProvider: String = "",
    val ocrModel: String = "",
    val detectedLanguages: String = "",
    val providerConfidence: Float? = null,
    val ocrGeometryJson: String = "",
    val ocrTimestamp: Long? = null,
    val ocrStatus: String = "",
    val ocrFailureReason: String = "",
    val ocrRetryCount: Int = 0,
    val followUpAt: Long? = null,
    val isFollowUpDone: Boolean = false,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
