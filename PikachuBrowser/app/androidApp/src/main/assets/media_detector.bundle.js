/**
 * Peach Browser Media Output Detector & Auto-Detection Engine
 * Real-time monitoring of HTML5 Audio, Video, and MediaSession playback.
 */
(function() {
    'use strict';
    if (window.__PEACH_MEDIA_DETECTOR_LOADED__) return;
    window.__PEACH_MEDIA_DETECTOR_LOADED__ = true;

    window.__PEACH_IS_MEDIA_ACTIVE__ = false;

    function evaluateMediaPlaying() {
        var isPlaying = false;
        try {
            var elements = document.querySelectorAll('audio, video');
            for (var i = 0; i < elements.length; i++) {
                var el = elements[i];
                if (!el.paused && !el.ended && el.readyState > 1 && el.currentTime > 0) {
                    isPlaying = true;
                    break;
                }
            }
            if (!isPlaying && navigator.mediaSession && navigator.mediaSession.playbackState === 'playing') {
                isPlaying = true;
            }
        } catch (e) {}

        window.__PEACH_IS_MEDIA_ACTIVE__ = isPlaying;

        try {
            if (window.PeachNativeBridge && window.PeachNativeBridge.onMediaStateChanged) {
                window.PeachNativeBridge.onMediaStateChanged(isPlaying);
            }
        } catch (e) {}

        try {
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.peachMedia) {
                window.webkit.messageHandlers.peachMedia.postMessage({ isPlaying: isPlaying });
            }
        } catch (e) {}

        return isPlaying;
    }

    window.__PEACH_PAUSE_ALL_MEDIA__ = function() {
        try {
            var elements = document.querySelectorAll('audio, video');
            for (var i = 0; i < elements.length; i++) {
                elements[i].pause();
            }
            window.__PEACH_IS_MEDIA_ACTIVE__ = false;
            return true;
        } catch (e) {
            return false;
        }
    };

    ['play', 'playing', 'timeupdate', 'volumechange'].forEach(function(evt) {
        document.addEventListener(evt, evaluateMediaPlaying, true);
    });

    ['pause', 'ended', 'suspend'].forEach(function(evt) {
        document.addEventListener(evt, function() {
            setTimeout(evaluateMediaPlaying, 500);
        }, true);
    });

    setInterval(evaluateMediaPlaying, 3000);
})();
