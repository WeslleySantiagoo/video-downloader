package com.weslley.wesdownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubeUrlValidatorTest {
    @Test
    fun `normaliza URLs de watch shorts e youtu be`() {
        val expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(expected, YouTubeUrlValidator.normalize("https://youtu.be/dQw4w9WgXcQ?t=4"))
        assertEquals(expected, YouTubeUrlValidator.normalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=abc"))
        assertEquals(expected, YouTubeUrlValidator.normalize("https://youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun `rejeita hosts parecidos e esquemas inseguros`() {
        assertThrows(AppError.InvalidUrl::class.java) {
            YouTubeUrlValidator.normalize("https://youtube.com.example.org/watch?v=dQw4w9WgXcQ")
        }
        assertThrows(AppError.InvalidUrl::class.java) {
            YouTubeUrlValidator.normalize("file://youtube.com/watch?v=dQw4w9WgXcQ")
        }
    }

    @Test
    fun `rejeita URL de playlist`() {
        assertThrows(AppError.Playlist::class.java) {
            YouTubeUrlValidator.normalize("https://youtube.com/playlist?list=PL123")
        }
    }
}

