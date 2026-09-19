package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = :type ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByType(type: NoteType): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE (title LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR url LIKE '%' || :query || '%' 
           OR urlDescription LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE followUpAt IS NOT NULL ORDER BY isFollowUpDone ASC, followUpAt ASC")
    fun getFollowUps(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET followUpAt = :followUpAt, isFollowUpDone = :isDone, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFollowUp(
        id: Long,
        followUpAt: Long?,
        isDone: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )
}
