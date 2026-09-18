package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.NoteType

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String {
        return value.name
    }

    @TypeConverter
    fun toNoteType(value: String): NoteType {
        return try {
            NoteType.valueOf(value)
        } catch (e: Exception) {
            NoteType.TEXT
        }
    }
}
