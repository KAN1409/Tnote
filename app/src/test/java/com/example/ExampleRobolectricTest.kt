package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import com.example.service.models.ModelManager
import com.example.service.speech.OfflineTranscriber
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
@Config(sdk = [35])
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
    assertEquals("Tnote", appName)
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

  @Test
  fun `follow up is persisted and can be completed`() = runBlocking {
    val id = database.noteDao().insertNote(NoteEntity(title = "Call supplier"))
    val tomorrow = System.currentTimeMillis() + 86_400_000L
    database.noteDao().updateFollowUp(id, tomorrow, false)
    val pending = database.noteDao().getFollowUps().first().single()
    assertEquals(tomorrow, pending.followUpAt)
    database.noteDao().updateFollowUp(id, tomorrow, true)
    assertEquals(true, database.noteDao().getFollowUps().first().single().isFollowUpDone)
  }

  @Test
  fun `model manager exposes embedded fallbacks and rejects missing downloads`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("offline_models", Context.MODE_PRIVATE).edit().clear().commit()
    context.filesDir.resolve("downloaded_models").deleteRecursively()
    val manager = ModelManager(context)

    assertEquals(ModelManager.VOICE_TINY, manager.state.value.activeVoiceId)
    assertEquals(ModelManager.OCR_FAST, manager.state.value.activeOcrId)
    assertEquals(true, ModelManager.VOICE_TINY in manager.state.value.installedIds)
    assertEquals(true, ModelManager.OCR_FAST in manager.state.value.installedIds)
    assertEquals(true, manager.activate(ModelManager.VOICE_BASE).isFailure)
    assertEquals(true, manager.activate(ModelManager.VOICE_SMALL).isFailure)
    assertEquals(true, manager.activate(ModelManager.OCR_BEST).isFailure)
  }

  @Test
  fun `repeated whisper hallucination is rejected`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val transcriber = OfflineTranscriber(context, ModelManager(context))

    assertEquals(true, transcriber.isRepetitionHallucination("نحن نحن نحن نحن نحن نحن"))
    assertEquals(
      false,
      transcriber.isRepetitionHallucination("ده اختبار جديد للترجمة This is a new transcription test")
    )
  }

  @Test
  fun `silence splitter preserves two spoken phrases`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val transcriber = OfflineTranscriber(context, ModelManager(context))
    val sampleRate = 16_000
    val phrase = FloatArray(sampleRate) { index -> if (index % 20 < 10) 0.2f else -0.2f }
    val silence = FloatArray(sampleRate)
    val recording = phrase + silence + phrase

    assertEquals(2, transcriber.splitOnSilence(recording, sampleRate).size)
    assertEquals(0, transcriber.splitOnSilence(silence, sampleRate).size)
  }
}
