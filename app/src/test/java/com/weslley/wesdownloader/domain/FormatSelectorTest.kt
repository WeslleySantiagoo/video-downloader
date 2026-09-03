package com.weslley.wesdownloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSelectorTest {
    @Test
    fun `ordena alturas e prefere mp4`() {
        val result = FormatSelector.videoOptions(
            listOf(
                RawFormat("web-1080", 1080, "webm", "vp9", "none", 20),
                RawFormat("mp4-360", 360, "mp4", "avc1", "none", 5),
                RawFormat("web-720", 720, "webm", "vp9", "none", 10),
                RawFormat("mp4-720", 720, "mp4", "avc1", "none", 11),
                RawFormat("audio", 0, "m4a", "none", "mp4a", 3),
            ),
        )

        assertEquals(listOf(360, 720, 1080), result.map { it.height })
        assertEquals("mp4-720", result[1].formatId)
        assertEquals("webm", result.last().container)
    }

    @Test
    fun `gera seletor com audio adequado ao container`() {
        val option = QualityOption("video", "137", "1080p", 1080, "mp4", null)
        val selector = FormatSelector.videoDownloadSelector(option)
        assertTrue(selector.startsWith("137+bestaudio[ext=m4a]"))
        assertTrue(selector.endsWith("best[height=1080]"))
    }
}

