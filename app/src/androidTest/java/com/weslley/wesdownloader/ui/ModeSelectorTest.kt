package com.weslley.wesdownloader.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.weslley.wesdownloader.domain.MediaMode
import com.weslley.wesdownloader.ui.theme.WesDownloaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModeSelectorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun alternaEntreVideoEAudio() {
        var selected = MediaMode.VIDEO
        compose.setContent {
            WesDownloaderTheme {
                ModeSelector(selected) { selected = it }
            }
        }

        compose.onNodeWithText("Video").assertIsDisplayed()
        compose.onNodeWithText("Audio").performClick()
        assertEquals(MediaMode.AUDIO, selected)
    }
}
