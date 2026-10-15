/**
 * Peach AI Browser — True Background Playback Engine
 * Scoped strictly to streaming media domains so non-media SPAs are never affected,
 * while exposing global Pause/Resume/Duck bridges for Audio Focus and Sleep Timer.
 */
(function initPeachBackgroundPlayback() {
    'use strict';

    const nativeMediaPause = HTMLMediaElement.prototype.pause;

    window.__PEACH_EXTERNAL_PAUSED__ = false;

    window.__PEACH_PAUSE_ALL_MEDIA__ = function() {
        try {
            window.__PEACH_EXTERNAL_PAUSED__ = true;
            const mediaElements = document.querySelectorAll('video, audio');
            mediaElements.forEach(media => {
                if (!media.paused) {
                    media.__peach_paused_by_focus = true;
                }
                media.__peach_sleep_timer_paused = true;
                media.__peach_user_paused = true;
                media.__peach_was_playing = false;
                try {
                    nativeMediaPause.apply(media);
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
                const shouldResume = media.__peach_paused_by_focus === true;
                media.__peach_paused_by_focus = false;
                media.__peach_sleep_timer_paused = false;
                media.__peach_user_paused = false;
                media.__peach_was_playing = true;
                if (shouldResume) {
                    try {
                        const playPromise = media.play();
                        if (playPromise !== undefined) {
                            playPromise.catch(() => {});
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
        'youtube.com', 'music.youtube.com', 'youtu.be', 'vimeo.com', 'dailymotion.com',
        'twitch.tv', 'soundcloud.com', 'spotify.com', 'music.apple.com',
        'gaana.com', 'jiosaavn.com', 'wynk.in', 'tidal.com',
        'deezer.com', 'pandora.com', 'audiomack.com', 'bandcamp.com'
    ];
    const isStreamingDomain = streamingDomains.some(d => currentHost === d || currentHost.endsWith('.' + d));
    if (!isStreamingDomain) {
        return;
    }

    try {
        if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isBackgroundPlayActive === 'function') {
            if (!window.PeachNativeBridge.isBackgroundPlayActive()) {
                return;
            }
        } else if (window.__PEACH_CONFIG__ && typeof window.__PEACH_CONFIG__.bgPlayActive === 'boolean') {
            if (!window.__PEACH_CONFIG__.bgPlayActive) {
                return;
            }
        }
    } catch (e) {}

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
            window.addEventListener(evtName, (e) => {
                e.stopImmediatePropagation();
            }, true);
            document.addEventListener(evtName, (e) => {
                e.stopImmediatePropagation();
            }, true);
        });

        let isUserInteracting = false;
        let interactionTimer = null;
        const setInteracting = () => {
            isUserInteracting = true;
            window.__PEACH_EXTERNAL_PAUSED__ = false;
            if (interactionTimer) clearTimeout(interactionTimer);
            interactionTimer = setTimeout(() => {
                isUserInteracting = false;
            }, 800);
        };

        ['click', 'touchstart', 'touchend', 'pointerdown', 'keydown'].forEach(evt => {
            window.addEventListener(evt, setInteracting, { capture: true, passive: true });
        });

        HTMLMediaElement.prototype.pause = function() {
            if (window.__PEACH_EXTERNAL_PAUSED__ || this.__peach_sleep_timer_paused || this.__peach_paused_by_focus) {
                return nativeMediaPause.apply(this, arguments);
            }
            if (isUserInteracting) {
                this.__peach_user_paused = true;
                return nativeMediaPause.apply(this, arguments);
            }
            let bgStillEnabled = true;
            try {
                if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isBackgroundPlayActive === 'function') {
                    bgStillEnabled = window.PeachNativeBridge.isBackgroundPlayActive();
                }
            } catch (e) {}

            if (bgStillEnabled && (this.tagName === 'VIDEO' || this.tagName === 'AUDIO') &&
                (currentHost.includes('youtube.com') || currentHost.includes('vimeo.com') || currentHost.includes('youtu.be'))
            ) {
                this.__peach_user_paused = false;
                return;
            }
            return nativeMediaPause.apply(this, arguments);
        };

        // Maintain window._lact every 30s to prevent YouTube's inactivity watchdog from pausing audio
        window._lact = Date.now();
        setInterval(() => {
            try {
                window._lact = Date.now();
            } catch (e) {}
        }, 30000);

    } catch (e) {}
})();
