/**
 * Peach AI Browser — True Background Playback Engine
 * Bypasses Page Visibility API, Fullscreen API, document.hidden, window.blur,
 * and suppresses automatic background pausing on YouTube, Vimeo, and media streaming sites.
 */
(function initPeachBackgroundPlayback() {
    'use strict';

    if (window.__PEACH_BG_PLAYBACK_ACTIVE__) {
        return;
    }
    window.__PEACH_BG_PLAYBACK_ACTIVE__ = true;

    try {
        // 1. Spoof Page Visibility API
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
        defineVisProperty(document, 'mozHidden', false);
        defineVisProperty(document, 'mozVisibilityState', 'visible');

        // 2. Spoof document.hasFocus()
        try {
            document.hasFocus = () => true;
            Document.prototype.hasFocus = () => true;
        } catch (e) {}

        // 3. Prevent property-based handlers (onvisibilitychange, onpagehide, onblur)
        try {
            Object.defineProperty(document, 'onvisibilitychange', {
                get: () => null,
                set: () => {},
                configurable: true
            });
            Object.defineProperty(window, 'onpagehide', {
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

        // 4. Intercept addEventListener to drop visibility and blur listeners
        const blockedEvents = new Set([
            'visibilitychange',
            'webkitvisibilitychange',
            'mozvisibilitychange',
            'pagehide',
            'blur'
        ]);

        const originalAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (blockedEvents.has(type)) {
                // Drop site background-detection event listeners
                return;
            }
            return originalAddEventListener.apply(this, arguments);
        };

        // 5. In case listeners were registered before this script, stop propagation in capture phase
        blockedEvents.forEach(evtName => {
            window.addEventListener(evtName, (e) => {
                e.stopImmediatePropagation();
            }, true);
            document.addEventListener(evtName, (e) => {
                e.stopImmediatePropagation();
            }, true);
        });

        // 6. User Interaction Tracker for Intentional Pause vs Auto-Pause
        let isUserInteracting = false;
        let interactionTimer = null;
        const setInteracting = () => {
            isUserInteracting = true;
            if (interactionTimer) clearTimeout(interactionTimer);
            interactionTimer = setTimeout(() => {
                isUserInteracting = false;
            }, 800);
        };

        ['click', 'touchstart', 'touchend', 'pointerdown', 'keydown'].forEach(evt => {
            window.addEventListener(evt, setInteracting, { capture: true, passive: true });
        });

        // 7. Intercept HTMLMediaElement.prototype.pause
        const originalPause = HTMLMediaElement.prototype.pause;
        HTMLMediaElement.prototype.pause = function() {
            if (this.__peach_sleep_timer_paused) {
                // Sleep timer requested pause
                return originalPause.apply(this, arguments);
            }
            if (isUserInteracting) {
                // Legitimate user pause via UI button
                this.__peach_user_paused = true;
                return originalPause.apply(this, arguments);
            }
            // Background auto-pause attempted by page scripts
            this.__peach_user_paused = false;
        };

        // 8. Sleep Timer App-Level Pause Bridge
        window.__PEACH_PAUSE_ALL_MEDIA__ = function() {
            try {
                const mediaElements = document.querySelectorAll('video, audio');
                mediaElements.forEach(media => {
                    media.__peach_sleep_timer_paused = true;
                    media.__peach_user_paused = true;
                    media.__peach_was_playing = false;
                    try {
                        originalPause.apply(media);
                    } catch (e) {}
                });
            } catch (err) {
                console.error('[Peach] Error in __PEACH_PAUSE_ALL_MEDIA__:', err);
            }
        };

        // 9. Auto-Resume Watchdog: if a media element was playing and got paused without user intent, resume it
        setInterval(() => {
            try {
                const videos = document.querySelectorAll('video, audio');
                videos.forEach(media => {
                    if (media.__peach_sleep_timer_paused) {
                        return; // Sleep timer explicitly paused this media — DO NOT resume
                    }
                    if (media.__peach_was_playing && media.paused && !media.__peach_user_paused && !isUserInteracting) {
                        const playPromise = media.play();
                        if (playPromise !== undefined) {
                            playPromise.catch(() => {});
                        }
                    } else if (!media.paused) {
                        media.__peach_was_playing = true;
                        media.__peach_user_paused = false;
                    }
                });
            } catch (e) {}
        }, 800);

        // 9. Prevent YouTube / streaming site idle timeouts ("Are you still watching?")
        setInterval(() => {
            try {
                if (typeof window._lact !== 'undefined') {
                    window._lact = Date.now();
                }
            } catch (e) {}
        }, 15000);

    } catch (e) {
        console.error('[Peach] Background playback initialization error:', e);
    }
})();
