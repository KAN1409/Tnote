package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.NoteEntity
import com.example.data.model.NoteType
import com.example.service.speech.PlayerState
import com.example.ui.components.NoteItemCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleNote = NoteEntity(
      id = 1L,
      type = NoteType.VOICE,
      title = "Voice Note Preview",
      content = "This is a transcribed voice note using Android speech recognition.",
      durationSeconds = 12
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        NoteItemCard(
          note = sampleNote,
          playerState = PlayerState(),
          onTogglePlayAudio = {},
          onEditClick = {},
          onDeleteClick = {},
          onTogglePin = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

