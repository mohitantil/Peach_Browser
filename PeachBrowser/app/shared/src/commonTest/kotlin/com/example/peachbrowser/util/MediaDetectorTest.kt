package com.example.peachbrowser.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaDetectorTest {

    @Test
    fun testRecognizedStreamingSites() {
        // YouTube & YouTube Music
        assertTrue(MediaDetector.isKnownStreamingSite("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://www.youtube.com/watch?v=123"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://music.youtube.com/watch?v=abc"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://youtu.be/xyz"))

        // Spotify
        assertTrue(MediaDetector.isKnownStreamingSite("https://open.spotify.com/track/123"))
        assertTrue(MediaDetector.isKnownStreamingSite("http://spotify.com"))

        // Indian and Global Audio Streamers
        assertTrue(MediaDetector.isKnownStreamingSite("https://gaana.com/song/track"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://www.jiosaavn.com/album/xyz"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://wynk.in/music/song/abc"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://music.apple.com/us/album/xyz"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://soundcloud.com/artist/track"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://tidal.com/browse/track/123"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://www.deezer.com/track/123"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://pandora.com/station/123"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://audiomack.com/song/track"))
        assertTrue(MediaDetector.isKnownStreamingSite("https://bandcamp.com/album/xyz"))
    }

    @Test
    fun testPortAndAuthorityHandling() {
        assertTrue(MediaDetector.isKnownStreamingSite("https://youtube.com:443/watch?v=123"))
        assertTrue(MediaDetector.isKnownStreamingSite("http://m.youtube.com:8080/"))
    }

    @Test
    fun testNegativeAndSpoofingPrevention() {
        // Phishing & attacker subdomains must not match
        assertFalse(MediaDetector.isKnownStreamingSite("https://notyoutube.com/watch"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://youtube.com.attacker.org/"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://fake-spotify.com/play"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://eviljiosaavn.com/song"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://mybandcamp.com/album"))

        // General non-streaming sites
        assertFalse(MediaDetector.isKnownStreamingSite("https://duckduckgo.com/?q=youtube"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://github.com/torvalds/linux"))
        assertFalse(MediaDetector.isKnownStreamingSite("https://en.wikipedia.org/wiki/Spotify"))

        // Empty and blank
        assertFalse(MediaDetector.isKnownStreamingSite(""))
        assertFalse(MediaDetector.isKnownStreamingSite("   "))
    }
}

