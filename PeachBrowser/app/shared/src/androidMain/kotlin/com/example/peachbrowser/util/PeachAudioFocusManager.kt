package com.example.peachbrowser.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.peachbrowser.webview.AndroidTabWebViewPool

class PeachAudioFocusManager(private val context: Context) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var hasAudioFocus = false

    @Volatile
    private var wasPausedTransiently = false

    var onPauseRequested: (() -> Unit)? = null
    var onResumeRequested: (() -> Unit)? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this, mainHandler)
                .build()
        }
    }

    @Synchronized
    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val am = audioManager ?: return false

        val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        if (hasAudioFocus) {
            wasPausedTransiently = false
        }
        return hasAudioFocus
    }

    @Synchronized
    fun abandonAudioFocus() {
        if (!hasAudioFocus && !wasPausedTransiently) return
        val am = audioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
        hasAudioFocus = false
        wasPausedTransiently = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        mainHandler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Phone call, WhatsApp status/video, or transient media interruption: pause immediately
                    wasPausedTransiently = true
                    hasAudioFocus = false
                    AndroidTabWebViewPool.pauseAllMedia()
                    onPauseRequested?.invoke()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    AndroidTabWebViewPool.unduckAllMedia()
                    if (wasPausedTransiently) {
                        wasPausedTransiently = false
                        AndroidTabWebViewPool.resumeActiveMedia()
                        onResumeRequested?.invoke()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    wasPausedTransiently = false
                    hasAudioFocus = false
                    AndroidTabWebViewPool.pauseAllMedia()
                    onPauseRequested?.invoke()
                    abandonAudioFocus()
                }
            }
        }
    }
}
