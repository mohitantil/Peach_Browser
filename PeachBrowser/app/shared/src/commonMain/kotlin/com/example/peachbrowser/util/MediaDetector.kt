package com.example.peachbrowser.util

object MediaDetector {
    private val STREAMING_DOMAINS = setOf(
        "youtube.com",
        "music.youtube.com",
        "youtu.be",
        "spotify.com",
        "gaana.com",
        "music.apple.com",
        "soundcloud.com",
        "jiosaavn.com",
        "wynk.in",
        "tidal.com",
        "deezer.com",
        "pandora.com",
        "audiomack.com",
        "bandcamp.com"
    )

    fun isKnownStreamingSite(url: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrl = url.lowercase().removePrefix("https://").removePrefix("http://")
        val host = cleanUrl.substringBefore("/").substringBefore("?").substringBefore(":")
        if (host.isBlank()) return false
        return STREAMING_DOMAINS.any { host == it || host.endsWith(".$it") }
    }
}
