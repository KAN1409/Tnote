package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var database: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Voice Notes", appName)
  }

  @Test
  fun `insert and retrieve voice note`() = runBlocking {
    val note = NoteEntity(
      type = NoteType.VOICE,
      title = "Voice Memo 1",
      content = "Transcribed speech text content",
      durationSeconds = 15
    )
    val id = database.noteDao().insertNote(note)
    val notes = database.noteDao().getAllNotes().first()
    assertEquals(1, notes.size)
    assertEquals("Voice Memo 1", notes[0].title)
    assertEquals(NoteType.VOICE, notes[0].type)
  }
}

