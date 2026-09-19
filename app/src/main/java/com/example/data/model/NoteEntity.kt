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
    val followUpAt: Long? = null,
    val isFollowUpDone: Boolean = false,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
