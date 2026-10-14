/**
 * Peach Browser Media Output Detector & Auto-Detection Engine
 * Deduplicated monitoring of HTML5 Audio and Video playback without JNI flooding.
 */
(function() {
    'use strict';
    if (window.__PEACH_MEDIA_DETECTOR_LOADED__) return;
    window.__PEACH_MEDIA_DETECTOR_LOADED__ = true;

    window.__PEACH_IS_MEDIA_ACTIVE__ = false;
    var lastReportedState = null;
    var pauseDebounceTimer = null;

    function notifyNative(isPlaying) {
        if (pauseDebounceTimer) {
            clearTimeout(pauseDebounceTimer);
            pauseDebounceTimer = null;
        }
        if (lastReportedState === isPlaying) {
            return;
        }
        lastReportedState = isPlaying;
        window.__PEACH_IS_MEDIA_ACTIVE__ = isPlaying;

        try {
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge.onMediaStateChanged === 'function') {
                window.PeachNativeBridge.onMediaStateChanged(isPlaying);
            }
        } catch (e) {}

        try {
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.peachMedia) {
                window.webkit.messageHandlers.peachMedia.postMessage({ isPlaying: isPlaying });
            }
        } catch (e) {}
    }

    function checkAnyMediaPlaying() {
        if (window.__PEACH_EXTERNAL_PAUSED__) {
            return false;
        }
        try {
            var elements = document.querySelectorAll('audio, video');
            for (var i = 0; i < elements.length; i++) {
                var el = elements[i];
                if (!el.paused && !el.ended && el.readyState >= 2) {
                    return true;
                }
            }
            if (navigator.mediaSession && navigator.mediaSession.playbackState === 'playing') {
                return true;
            }
        } catch (e) {}
        return false;
    }

    function evaluateMediaPlaying() {
        var isPlaying = checkAnyMediaPlaying();
        if (isPlaying) {
            notifyNative(true);
        } else if (lastReportedState === true && !pauseDebounceTimer) {
            // Debounce false state by 1200ms so track transitions / buffering don't drop & re-request AudioFocus
            pauseDebounceTimer = setTimeout(function() {
                pauseDebounceTimer = null;
                if (!checkAnyMediaPlaying()) {
                    notifyNative(false);
                }
            }, 1200);
        }
        return isPlaying;
    }

    ['play', 'playing'].forEach(function(evt) {
        document.addEventListener(evt, function(e) {
            if (e && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                e.target.__peach_sleep_timer_paused = false;
                e.target.__peach_paused_by_focus = false;
            }
            window.__PEACH_EXTERNAL_PAUSED__ = false;
            notifyNative(true);
        }, true);
    });

    ['pause', 'ended', 'emptied'].forEach(function(evt) {
        document.addEventListener(evt, function() {
            if (window.__PEACH_EXTERNAL_PAUSED__) {
                notifyNative(false);
            } else {
                evaluateMediaPlaying();
            }
        }, true);
    });

    setInterval(evaluateMediaPlaying, 3000);
})();
