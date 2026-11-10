package com.example.peachbrowser.presentation.mvi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserMviStateTest {

    @Test
    fun testSleepTimerRemainingFormatting() {
        val timer15Min = SleepTimerState(
            isActive = true,
            remainingSeconds = 900,
            totalSeconds = 900,
            action = SleepTimerAction.PAUSE_PLAYBACK
        )
        assertEquals("15:00", timer15Min.formattedRemaining)

        val timer45Sec = SleepTimerState(
            isActive = true,
            remainingSeconds = 45,
            totalSeconds = 900,
            action = SleepTimerAction.PAUSE_PLAYBACK
        )
        assertEquals("00:45", timer45Sec.formattedRemaining)

        val timer1Min5Sec = SleepTimerState(
            isActive = true,
            remainingSeconds = 65,
            totalSeconds = 900,
            action = SleepTimerAction.CLOSE_TAB
        )
        assertEquals("01:05", timer1Min5Sec.formattedRemaining)
    }

    @Test
    fun testSiteShieldConfigDefaults() {
        val config = SiteShieldConfig()
        assertTrue(config.shieldsUp)
        assertTrue(config.blockTrackersAndAds)
        assertFalse(config.blockScripts)
        assertTrue(config.blockFingerprinting)
        assertFalse(config.blockCookies)
        assertEquals(0, config.blockedCount)
    }

    @Test
    fun testDefaultSiteExceptionsContainCriticalPortals() {
        // Critical AI and Identity providers must be in the default exception list to prevent broken SPAs and login flows
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("gemini.google.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("chatgpt.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("claude.ai"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("accounts.google.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("appleid.apple.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("login.microsoftonline.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("clerk.com"))
        assertTrue(DEFAULT_SITE_EXCEPTIONS.contains("auth0.com"))
    }

    @Test
    fun testDownloadItemProgressAndCompletion() {
        val activeDownload = DownloadItem(
            id = 1L,
            fileName = "song.mp3",
            url = "https://example.com/song.mp3",
            progress = 50,
            isCompleted = false,
            bytesDownloaded = 500L,
            totalBytes = 1000L
        )
        assertFalse(activeDownload.isCompleted)
        assertEquals(50, activeDownload.progress)

        val completedDownload = activeDownload.copy(
            progress = 100,
            isCompleted = true,
            bytesDownloaded = 1000L
        )
        assertTrue(completedDownload.isCompleted)
        assertEquals(100, completedDownload.progress)
    }

    @Test
    fun testMediaMetadataStateEquality() {
        val meta1 = MediaMetadataState(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            artworkUrl = "https://example.com/queen.jpg",
            isPlaying = true
        )
        val meta2 = MediaMetadataState(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            artworkUrl = "https://example.com/queen.jpg",
            isPlaying = true
        )
        val metaPaused = meta1.copy(isPlaying = false)

        assertEquals(meta1, meta2)
        assertFalse(meta1 == metaPaused)
        assertFalse(metaPaused.isPlaying)
    }
}

