package com.weslley.wesdownloader.download

object FileNames {
    private val unsafe = Regex("[^A-Za-z0-9._ -]+")
    private val whitespace = Regex("\\s+")

    fun sanitize(value: String): String = value
        .replace(unsafe, "")
        .replace(whitespace, " ")
        .trim(' ', '.', '-')
        .take(100)
        .ifBlank { "midia" }
}
