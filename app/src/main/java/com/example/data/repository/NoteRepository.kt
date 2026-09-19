package com.example.data.repository

import com.example.data.local.NoteDao
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun getNotesByType(type: NoteType): Flow<List<NoteEntity>> = noteDao.getNotesByType(type)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)

    fun getFollowUps(): Flow<List<NoteEntity>> = noteDao.getFollowUps()

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

    suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)

    suspend fun togglePin(id: Long, currentPinState: Boolean) {
        noteDao.updatePinStatus(id, !currentPinState)
    }

    suspend fun toggleImportant(id: Long, currentState: Boolean) {
        noteDao.updateImportantStatus(id, !currentState)
    }

    suspend fun setFollowUp(id: Long, followUpAt: Long?) {
        noteDao.updateFollowUp(id, followUpAt, false)
    }

    suspend fun toggleFollowUpDone(note: NoteEntity) {
        noteDao.updateFollowUp(note.id, note.followUpAt, !note.isFollowUpDone)
    }
}
