/**
 * Peach Browser - Real-Time Media Output Detector & Remote Playback Controller
 */
(function () {
    'use strict';
    if (window.__PEACH_MEDIA_DETECTOR_LOADED__) return;
    window.__PEACH_MEDIA_DETECTOR_LOADED__ = true;

    window.__PEACH_IS_MEDIA_ACTIVE__ = false;
    var lastReportedState = null;
    var lastMetadataSignature = '';
    var pauseDebounceTimer = null;
    var actionHandlers = {};

    try {
        if (navigator.mediaSession && typeof navigator.mediaSession.setActionHandler === 'function') {
            var origSetActionHandler = navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
            navigator.mediaSession.setActionHandler = function (action, handler) {
                if (typeof handler === 'function') {
                    actionHandlers[action] = handler;
                } else {
                    delete actionHandlers[action];
                }
                return origSetActionHandler(action, handler);
            };
        }
    } catch (e) { }

    function extractMetadata(isPlaying) {
        var title = document.title || '';
        var artist = location.hostname || '';
        var artworkUrl = '';

        try {
            if (navigator.mediaSession && navigator.mediaSession.metadata) {
                var meta = navigator.mediaSession.metadata;
                if (meta.title) title = meta.title;
                if (meta.artist) artist = meta.artist;
                if (meta.artwork && meta.artwork.length > 0) {
                    artworkUrl = meta.artwork[meta.artwork.length - 1].src || '';
                }
            }
        } catch (e) { }

        var sig = title + '|' + artist + '|' + isPlaying;
        if (sig !== lastMetadataSignature) {
            lastMetadataSignature = sig;
            try {
                if (window.PeachNativeBridge && typeof window.PeachNativeBridge.onMediaMetadataUpdated === 'function') {
                    window.PeachNativeBridge.onMediaMetadataUpdated(title, artist, artworkUrl, isPlaying);
                }
            } catch (e) { }
        }
    }

    function notifyNative(isPlaying) {
        if (pauseDebounceTimer) {
            clearTimeout(pauseDebounceTimer);
            pauseDebounceTimer = null;
        }
        extractMetadata(isPlaying);
        if (lastReportedState === isPlaying) {
            return;
        }
        lastReportedState = isPlaying;
        window.__PEACH_IS_MEDIA_ACTIVE__ = isPlaying;

        try {
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge.onMediaStateChanged === 'function') {
                window.PeachNativeBridge.onMediaStateChanged(isPlaying);
            }
        } catch (e) { }

        try {
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.peachMedia) {
                window.webkit.messageHandlers.peachMedia.postMessage({ isPlaying: isPlaying });
            }
        } catch (e) { }
    }

            function getPrimaryMediaElement() {
                var elements = document.querySelectorAll('video, audio');
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    if (!el.paused && !el.ended) return el;
                }
                return elements.length > 0 ? elements[0] : null;
            }

            window.__PEACH_MEDIA_CONTROL__ = function (action) {
                try {
                    var el = getPrimaryMediaElement();
                    if (action === 'play') {
                        window.__PEACH_EXTERNAL_PAUSED__ = false;
                        if (typeof actionHandlers['play'] === 'function') {
                            actionHandlers['play']();
                        } else if (el) {
                            el.__peach_sleep_timer_paused = false;
                            var p = el.play();
                            if (p && typeof p.catch === 'function') p.catch(function () { });
                        }
                        notifyNative(true);
                    } else if (action === 'pause') {
                        window.__PEACH_EXTERNAL_PAUSED__ = true;
                        if (typeof actionHandlers['pause'] === 'function') {
                            actionHandlers['pause']();
                        }
                        document.querySelectorAll('video, audio').forEach(function (m) {
                            try {
                                m.__peach_sleep_timer_paused = true;
                                m.pause();
                            } catch (e) { }
                        });
                        notifyNative(false);
                    } else if (action === 'next') {
                        window.__PEACH_EXTERNAL_PAUSED__ = false;
                        if (typeof actionHandlers['nexttrack'] === 'function') {
                            actionHandlers['nexttrack']();
                            return;
                        }
                        var nextBtn = document.querySelector('.ytp-next-button, .next-button, [aria-label*="Next"], [title*="Next"]');
                        if (nextBtn) {
                            nextBtn.click();
                        } else if (el && isFinite(el.duration)) {
                            el.currentTime = Math.min(el.duration, el.currentTime + 10);
                        }
                    } else if (action === 'previous') {
                        window.__PEACH_EXTERNAL_PAUSED__ = false;
                        if (typeof actionHandlers['previoustrack'] === 'function') {
                            actionHandlers['previoustrack']();
                            return;
                        }
                        var prevBtn = document.querySelector('.ytp-prev-button, .previous-button, [aria-label*="Previous"], [title*="Previous"]');
                        if (prevBtn && (!el || el.currentTime < 5)) {
                            prevBtn.click();
                        } else if (el) {
                            el.currentTime = Math.max(0, el.currentTime - 10);
                        }
                    }
                } catch (e) { }
            };

            function checkAnyMediaPlaying() {
                if (window.__PEACH_EXTERNAL_PAUSED__) return false;
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
                } catch (e) { }
                return false;
            }

            function evaluateMediaPlaying() {
                var isPlaying = checkAnyMediaPlaying();
                if (isPlaying) {
                    notifyNative(true);
                } else if (lastReportedState === true && !pauseDebounceTimer) {
                    pauseDebounceTimer = setTimeout(function () {
                        pauseDebounceTimer = null;
                        if (!checkAnyMediaPlaying()) {
                            notifyNative(false);
                        }
                    }, 1200);
                }
                return isPlaying;
            }

                ['play', 'playing', 'loadedmetadata'].forEach(function (evt) {
                    document.addEventListener(evt, function (e) {
                        if (e && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            e.target.__peach_sleep_timer_paused = false;
                            window.__PEACH_EXTERNAL_PAUSED__ = false;
                            notifyNative(!e.target.paused);
                        }
                    }, true);
                });

                ['pause', 'ended', 'emptied'].forEach(function (evt) {
                            document.addEventListener(evt, function (e) {
                                if (e && e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                                    if (window.__PEACH_EXTERNAL_PAUSED__) {
                                        notifyNative(false);
                                    } else {
                                        evaluateMediaPlaying();
                                    }
                                }
                            }, true);
                        });
                })();
