package com.weslley.wesdownloader.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test
    fun `remove caracteres perigosos e limita tamanho`() {
        assertEquals("video teste", FileNames.sanitize("../video: teste?"))
        assertTrue(FileNames.sanitize("a".repeat(200)).length <= 100)
    }

    @Test
    fun `usa nome padrao quando vazio`() {
        assertEquals("midia", FileNames.sanitize("///"))
    }
}

