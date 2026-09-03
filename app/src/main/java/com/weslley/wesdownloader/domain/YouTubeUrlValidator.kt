package com.weslley.wesdownloader.domain

import java.net.URI

object YouTubeUrlValidator {
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")
    private val acceptedHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )

    fun normalize(rawUrl: String): String {
        val value = rawUrl.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: throw AppError.InvalidUrl()
        if (uri.scheme?.lowercase() !in setOf("http", "https")) throw AppError.InvalidUrl()
        val host = uri.host?.lowercase() ?: throw AppError.InvalidUrl()
        if (host !in acceptedHosts) throw AppError.InvalidUrl()

        val path = uri.path.orEmpty().trim('/')
        if (path == "playlist" || path.startsWith("playlist/")) throw AppError.Playlist()

        val id = when {
            host.endsWith("youtu.be") -> path.substringBefore('/')
            path == "watch" -> queryParameter(uri.rawQuery, "v")
            path.startsWith("shorts/") -> path.substringAfter("shorts/").substringBefore('/')
            path.startsWith("embed/") -> path.substringAfter("embed/").substringBefore('/')
            path.startsWith("live/") -> path.substringAfter("live/").substringBefore('/')
            else -> null
        }
        if (id == null || !videoId.matches(id)) throw AppError.InvalidUrl()
        return "https://www.youtube.com/watch?v=$id"
    }

    private fun queryParameter(query: String?, name: String): String? = query
        ?.split('&')
        ?.asSequence()
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it.firstOrNull() == name }
        ?.getOrNull(1)
}

