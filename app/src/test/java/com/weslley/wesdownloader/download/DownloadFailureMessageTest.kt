package com.weslley.wesdownloader.download

import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailureMessageTest {
    @Test
    fun `identifica falha de combinacao no ffmpeg`() {
        val message = DownloadFailureMessage.from(IllegalStateException("ffmpeg postprocess failed"))

        assertTrue(message.contains("combinar"))
    }

    @Test
    fun `identifica interrupcao http do youtube`() {
        val message = DownloadFailureMessage.from(IllegalStateException("HTTP Error 403: Forbidden"))

        assertTrue(message.contains("interrompeu"))
    }
}
