/**
 * Peach AI Browser — Background Playback & Autoplay Engine
 *
 * Designed for 1-tap playback and continuous autoplay in foreground and background.
 *
 * Invariants:
 * 1. ZERO prototype patching of HTMLMediaElement.prototype.pause or .play.
 *    The player state machine is 100% native in the foreground, enabling 1-tap play
 *    and seamless cueing of next tracks.
 * 2. Document.prototype.hasFocus returns true so YouTube/YT Music's autoplay
 *    and watchdog logic never halts playback when minimized.
 * 3. Page visibility is spoofed (hidden = false, visibilityState = 'visible')
 *    and visibilitychange/blur events are blocked.
 * 4. Background re-arm is active ONLY when isAppBackgrounded() is true and
 *    the media has not ended (allowing autoplay-next to advance uninterrupted).
 */
(function initPeachBackgroundPlayback() {
    'use strict';

    if (typeof window.__PEACH_EXTERNAL_PAUSED__ !== 'boolean') {
        window.__PEACH_EXTERNAL_PAUSED__ = false;
    }

    function bridgeCall(name) {
        try {
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge[name] === 'function') {
                return !!window.PeachNativeBridge[name]();
            }
        } catch (e) {}
        return false;
    }

    function bgPlayEnabled() {
        if (bridgeCall('isBackgroundPlayActive')) return true;
        try {
            if (window.__PEACH_CONFIG__ && typeof window.__PEACH_CONFIG__.bgPlayActive === 'boolean') {
                return !!window.__PEACH_CONFIG__.bgPlayActive;
            }
        } catch (e) {}
        return true;
    }

    function isAppBackgrounded() {
        if (bridgeCall('isAppBackgrounded')) return true;
        return window.__PEACH_APP_BACKGROUNDED__ === true;
    }

    window.__PEACH_PAUSE_ALL_MEDIA__ = function() {
        try {
            window.__PEACH_EXTERNAL_PAUSED__ = true;
            const mediaElements = document.querySelectorAll('video, audio');
            mediaElements.forEach(media => {
                media.__peach_sleep_timer_paused = true;
                try {
                    media.pause();
                } catch (e) {}
            });
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge.onMediaStateChanged === 'function') {
                window.PeachNativeBridge.onMediaStateChanged(false);
            }
        } catch (err) {}
    };

    window.__PEACH_RESUME_ALL_MEDIA__ = function() {
        try {
            window.__PEACH_EXTERNAL_PAUSED__ = false;
            const mediaElements = document.querySelectorAll('video, audio');
            mediaElements.forEach(media => {
                media.__peach_sleep_timer_paused = false;
                if (media.paused && !media.ended && media.currentTime > 0) {
                    try {
                        const p = media.play();
                        if (p !== undefined && p && typeof p.catch === 'function') {
                            p.catch(() => {});
                        }
                    } catch (e) {}
                }
            });
        } catch (err) {}
    };

    window.__PEACH_DUCK_MEDIA__ = function(duck) {
        try {
            const mediaElements = document.querySelectorAll('video, audio');
            mediaElements.forEach(media => {
                if (duck) {
                    if (typeof media.__peach_orig_volume !== 'number') {
                        media.__peach_orig_volume = media.volume;
                    }
                    media.volume = Math.max(0.15, (media.__peach_orig_volume || 1.0) * 0.25);
                } else if (typeof media.__peach_orig_volume === 'number') {
                    media.volume = media.__peach_orig_volume;
                    delete media.__peach_orig_volume;
                }
            });
        } catch (e) {}
    };

    if (window.__PEACH_BG_PLAYBACK_ACTIVE__) {
        return;
    }

    const currentHost = (location.hostname || '').toLowerCase();
    const streamingDomains = [
        'youtube.com', 'music.youtube.com', 'youtu.be', 'youtube-nocookie.com',
        'vimeo.com', 'dailymotion.com', 'twitch.tv', 'soundcloud.com', 'spotify.com',
        'music.apple.com', 'gaana.com', 'jiosaavn.com', 'wynk.in', 'tidal.com',
        'deezer.com', 'pandora.com', 'audiomack.com', 'bandcamp.com'
    ];

    const isStreamingDomain = streamingDomains.some(d => currentHost === d || currentHost.endsWith('.' + d));
    if (!isStreamingDomain) {
        return;
    }

    if (!bgPlayEnabled()) {
        return;
    }

    window.__PEACH_BG_PLAYBACK_ACTIVE__ = true;

    try {
        const defineVisProperty = (target, prop, val) => {
            try {
                Object.defineProperty(target, prop, {
                    get: () => val,
                    set: () => {},
                    configurable: true,
                    enumerable: true
                });
            } catch (e) {}
        };

        defineVisProperty(document, 'hidden', false);
        defineVisProperty(document, 'visibilityState', 'visible');
        defineVisProperty(document, 'webkitHidden', false);
        defineVisProperty(document, 'webkitVisibilityState', 'visible');

        // Always report focused so YouTube, YT Music, and HTML5 players
        // know the tab is active and continue autoplay-next seamlessly.
        try {
            Object.defineProperty(Document.prototype, 'hasFocus', {
                value: function hasFocus() { return true; },
                writable: true,
                configurable: true
            });
        } catch (e) {}

        try {
            Object.defineProperty(document, 'onvisibilitychange', {
                get: () => null,
                set: () => {},
                configurable: true
            });
            Object.defineProperty(window, 'onblur', {
                get: () => null,
                set: () => {},
                configurable: true
            });
        } catch (e) {}

        const blockedEvents = new Set([
            'visibilitychange',
            'webkitvisibilitychange',
            'mozvisibilitychange',
            'blur'
        ]);

        const originalAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (blockedEvents.has(type) && (this === document || this === window)) {
                return;
            }
            return originalAddEventListener.apply(this, arguments);
        };

        blockedEvents.forEach(evtName => {
            const killer = (e) => { e.stopImmediatePropagation(); };
            originalAddEventListener.call(window, evtName, killer, true);
            originalAddEventListener.call(document, evtName, killer, true);
        });

        // Background supervisor: active ONLY while backgrounded.
        // In the foreground, there is zero interference with pause events.
        // If a track finishes (ended === true), supervisor does not re-play track 1,
        // allowing YouTube's native autoplay queue to load and play track 2.
        document.addEventListener('pause', function(e) {
            try {
                const el = e && e.target;
                if (!el || (el.tagName !== 'VIDEO' && el.tagName !== 'AUDIO')) return;

                // Foreground: 100% native behavior.
                if (!isAppBackgrounded()) return;
                if (window.__PEACH_EXTERNAL_PAUSED__ || el.__peach_sleep_timer_paused) return;
                if (!bgPlayEnabled()) return;

                // If track ended, do NOT re-arm old track; let autoplay advance to next track!
                if (el.ended) return;

                setTimeout(() => {
                    try {
                        if (!isAppBackgrounded()) return;
                        if (window.__PEACH_EXTERNAL_PAUSED__ || el.__peach_sleep_timer_paused) return;
                        if (!bgPlayEnabled()) return;
                        if (!el.paused || el.ended) return;

                        const p = el.play();
                        if (p !== undefined && p && typeof p.catch === 'function') {
                            p.catch(() => {});
                        }
                    } catch (err) {}
                }, 300);
            } catch (err) {}
        }, true);

        // Keep YouTube inactivity watchdog satisfied
        window._lact = Date.now();
        setInterval(() => {
            try { window._lact = Date.now(); } catch (e) {}
        }, 20000);

    } catch (e) {}
})();
