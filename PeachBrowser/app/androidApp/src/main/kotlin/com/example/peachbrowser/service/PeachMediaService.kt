package com.example.peachbrowser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.peachbrowser.MainActivity
import com.example.peachbrowser.webview.AndroidTabWebViewPool

class PeachMediaService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSession? = null

    private var currentTitle: String = "Peach Browser"
    private var currentArtist: String = "Background Media Active"
    private var isPlaying: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "PeachBrowserMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    AndroidTabWebViewPool.resumeActiveMedia()
                    updateMediaSessionAndNotification()
                }

                override fun onPause() {
                    isPlaying = false
                    AndroidTabWebViewPool.pauseAllMedia()
                    updateMediaSessionAndNotification()
                }

                override fun onSkipToNext() {
                    isPlaying = true
                    AndroidTabWebViewPool.skipToNextMedia()
                    updateMediaSessionAndNotification()
                }

                override fun onSkipToPrevious() {
                    isPlaying = true
                    AndroidTabWebViewPool.skipToPreviousMedia()
                    updateMediaSessionAndNotification()
                }

                override fun onStop() {
                    AndroidTabWebViewPool.pauseAllMedia()
                    stopPlaybackService()
                }
            })
            isActive = true
        }
        updateMediaSessionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AndroidTabWebViewPool.pauseAllMedia()
                stopPlaybackService()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                isPlaying = !isPlaying
                if (isPlaying) {
                    AndroidTabWebViewPool.resumeActiveMedia()
                } else {
                    AndroidTabWebViewPool.pauseAllMedia()
                }
                updateMediaSessionAndNotification()
                return START_STICKY
            }
            ACTION_NEXT -> {
                isPlaying = true
                AndroidTabWebViewPool.skipToNextMedia()
                updateMediaSessionAndNotification()
                return START_STICKY
            }
            ACTION_PREV -> {
                isPlaying = true
                AndroidTabWebViewPool.skipToPreviousMedia()
                updateMediaSessionAndNotification()
                return START_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    currentTitle = it
                }
                intent.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                    currentArtist = it
                }
                if (intent.hasExtra(EXTRA_IS_PLAYING)) {
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                }
                startForegroundPlayback()
                return START_STICKY
            }
        }

        startForegroundPlayback()
        return START_STICKY
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
            )
            .setState(stateCode, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (isPlaying) 1.0f else 0f)
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentArtist)
            .build()
        session.setMetadata(metadata)
    }

    private fun updateMediaSessionAndNotification() {
        updateMediaSessionState()
        if (isPlaying) {
            acquireLocks()
        } else {
            releaseLocks()
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startForegroundPlayback() {
        updateMediaSessionState()
        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (inner: Exception) {
                inner.printStackTrace()
            }
        }

        if (isPlaying) {
            acquireLocks()
        } else {
            releaseLocks()
        }
    }

    private fun stopPlaybackService() {
        isServiceRunning = false
        releaseLocks()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PeachBrowser::MediaStreamingWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(6 * 60 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(lockType, "PeachBrowser::MediaStreamingWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            wifiLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PeachMediaService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            serviceActionPendingIntent(ACTION_PREV, 101)
        ).build()

        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            serviceActionPendingIntent(ACTION_PLAY_PAUSE, 102)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            serviceActionPendingIntent(ACTION_NEXT, 103)
        ).build()

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            serviceActionPendingIntent(ACTION_STOP, 104)
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(serviceActionPendingIntent(ACTION_STOP, 105))
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent media playback controls for Peach Browser"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaybackService()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        releaseLocks()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "peach_media_playback"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.example.peachbrowser.action.START_BG_PLAY"
        const val ACTION_UPDATE = "com.example.peachbrowser.action.UPDATE_BG_PLAY"
        const val ACTION_PLAY_PAUSE = "com.example.peachbrowser.action.PLAY_PAUSE_BG_PLAY"
        const val ACTION_NEXT = "com.example.peachbrowser.action.NEXT_BG_PLAY"
        const val ACTION_PREV = "com.example.peachbrowser.action.PREV_BG_PLAY"
        const val ACTION_STOP = "com.example.peachbrowser.action.STOP_BG_PLAY"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        @Volatile
        var isServiceRunning: Boolean = false

        fun startOrUpdate(
            context: Context,
            title: String = "Peach Browser",
            artist: String = "Background Streaming",
            isPlaying: Boolean = true
        ) {
            try {
                isServiceRunning = true
                val intent = Intent(context, PeachMediaService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_ARTIST, artist)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            if (!isServiceRunning) return
            try {
                isServiceRunning = false
                context.stopService(Intent(context, PeachMediaService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
