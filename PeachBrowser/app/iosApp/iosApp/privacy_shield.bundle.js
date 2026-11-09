/**
 * Peach AI Browser — Privacy Shield & Ad Neutralizer Engine (Nov 2026)
 */
(function initPeachShield() {
    'use strict';

    if (window.__PEACH_SHIELD_ACTIVE__) return;

    let currentHost = '';
    try {
        currentHost = (location.hostname || '').toLowerCase();
        if (!currentHost && window.top && window.top.location) {
            currentHost = (window.top.location.hostname || '').toLowerCase();
        }
} catch (e) { }

if (!currentHost) return;

const defaultExceptionDomains = [
    'chatgpt.com', 'openai.com', 'auth.openai.com', 'oaistatic.com', 'oaiusercontent.com',
    'claude.ai', 'anthropic.com',
    'gemini.google.com', 'aistudio.google.com',
    'accounts.google.com', 'appleid.apple.com', 'login.microsoftonline.com', 'login.live.com',
    'github.com', 'auth0.com', 'clerk.com', 'clerk.accounts.dev',
    'gstatic.com', 'googleapis.com',
    'qwen.ai', 'qwenlm.ai', 'aliyun.com', 'alicdn.com',
    'z.ai', 'chat.z.ai',
    'deepseek.com', 'openrouter.ai', 'huggingface.co',
    'cloudflare.com', 'challenges.cloudflare.com'
];

if (defaultExceptionDomains.some(d => currentHost === d || currentHost.endsWith('.' + d))) {
    return;
}

        try {
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isShieldActive === 'function') {
                if (!window.PeachNativeBridge.isShieldActive(currentHost)) {
                    return;
                }
            } else if (window.__PEACH_CONFIG__) {
                if (window.__PEACH_CONFIG__.isSiteException === true || window.__PEACH_CONFIG__.shieldActive === false) {
                    return;
                }
            }
        } catch (e) { }

        window.__PEACH_SHIELD_ACTIVE__ = true;

        const COSMETIC_STYLES = [
            'ins.adsbygoogle',
            '[id^="google_ads_iframe"]',
            '[id^="div-gpt-ad"]',
            'iframe[src*="doubleclick.net"]',
            'iframe[src*="googlesyndication.com"]',
            'iframe[src*="googleadservices.com"]',
            '.trc_related_container',
            '.OUTBRAIN',
            '[data-widget-type="taboola"]',
            '[id^="taboola-"]',
            // Desktop YouTube Ads
            'ytd-ad-slot-renderer',
            'ytd-in-feed-ad-layout-renderer',
            'ytd-action-companion-ad-renderer',
            'ytd-promoted-sparkles-web-renderer',
            'ytd-display-ad-renderer',
            'ytd-promoted-video-renderer',
            'ytd-compact-promoted-video-renderer',
            'ytd-compact-promoted-item-renderer',
            'ytd-banner-promo-renderer',
            'ytd-statement-banner-renderer',
            'ytd-mealbar-promo-renderer',
            'ytmusic-mealbar-promo-renderer',
            '#masthead-ad',
            '#player-ads',
            '.ytp-ad-overlay-container',
            '.ytp-ad-overlay-slot',
            '.ytp-ad-module',
            '.ytp-ad-player-overlay',
            '.ytp-ad-player-overlay-layout',
            '.ytp-ad-progress',
            '.ytp-ad-progress-list',
            'ytd-enforcement-message-view-model',
            'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)',
            'ytd-rich-item-renderer:has([aria-label*="Sponsored" i])',
            'ytd-compact-video-renderer:has([aria-label*="Sponsored" i])',
            'ytd-video-renderer:has([aria-label*="Sponsored" i])',
            'ytd-reel-shelf-renderer:has([aria-label*="Sponsored" i])',
            // Mobile YouTube Ads (m.youtube.com feed & recommendation sponsors)
            'ytm-promoted-sparkles-web-renderer',
            'ytm-promoted-sparkles-text-search-renderer',
            'ytm-companion-ad-renderer',
            'ytm-single-item-companion-ad-renderer',
            'ytm-ad-slot-renderer',
            'ytm-in-feed-ad-layout-renderer',
            'ytm-promoted-item-renderer',
            'ytm-compact-promoted-item-renderer',
            'ytm-compact-promoted-video-renderer',
            'ytm-promoted-video-renderer',
            'ytm-statement-banner-renderer',
            'ytm-banner-promo-renderer',
            'ytm-mealbar-promo-renderer',
            'ytm-paid-content-overlay-renderer',
            'ytm-reel-shelf-renderer:has([aria-label*="Sponsored" i])',
            'ytm-video-with-context-renderer:has([aria-label*="Sponsored" i])',
            'ytm-compact-video-renderer:has([aria-label*="Sponsored" i])',
            'ytm-item-section-renderer:has(.ad-badge)',
            'ytm-shelf-renderer:has(.ad-badge)',
            '.ad-badge',
            '.ytm-badge-and-byline-item-byline:has(.ad-badge)',
            '[aria-label*="Sponsored" i]',
            '[aria-label="Ad"]',
            '[aria-label="Sponsored"]',
            '.ad-container',
            '.ad-div',
            '[data-ad-id]',
            '[data-ad-impressions]',
            '.sparkles-light-cta',
            '.companion-ad-container'
        ].join(',\n') + ' {\n    display: none !important;\n}';

        function injectCosmeticCSS() {
            try {
                if (document.getElementById('peach-shield-css')) return true;
                    if (document.head || document.documentElement) {
                        const target = document.head || document.documentElement;
                        const styleEl = document.createElement('style');
                        styleEl.id = 'peach-shield-css';
                        styleEl.textContent = COSMETIC_STYLES;
                        target.appendChild(styleEl);
                        return true;
                    }
        } catch (e) { }
        return false;
    }

    if (!injectCosmeticCSS()) {
    document.addEventListener('DOMContentLoaded', injectCosmeticCSS, { once: true });
}

const isYouTubeDomain = (
    currentHost === 'youtube.com' ||
    currentHost === 'www.youtube.com' ||
    currentHost === 'm.youtube.com' ||
    currentHost === 'music.youtube.com' ||
    currentHost.endsWith('.youtube.com')
);

if (!isYouTubeDomain) return;

        // --- YouTube API Payload Interceptor: Strips Ad Breaks at the Network JSON Layer ---
        function setupYouTubePayloadInterceptor() {
            try {
                if (window.__PEACH_YT_INTERCEPTED__) return;
                window.__PEACH_YT_INTERCEPTED__ = true;

                function stripAdsFromPlayerResponse(json) {
                    if (!json || typeof json !== 'object') return json;
                    // Set to empty arrays so YouTube client code doesn't throw on .length or forEach
                    json.adPlacements = [];
                    json.adSlots = [];
                    json.playerAds = [];
                    if (json.adBreakHeartbeatParams) delete json.adBreakHeartbeatParams;
                    if (json.adBreakParams) delete json.adBreakParams;
                    if (json.adParams) delete json.adParams;
                    if (json.playerConfig && json.playerConfig.adPlacementConfig) {
                        delete json.playerConfig.adPlacementConfig;
                    }
                    return json;
                }

                                    function isSponsoredItem(item) {
                                        if (!item || typeof item !== 'object') return false;
                                        if (item.adSlotRenderer ||
                                            item.promotedSparklesWebRenderer ||
                                            item.promotedSparklesTextSearchRenderer ||
                                            item.promotedItemRenderer ||
                                            item.compactPromotedItemRenderer ||
                                            item.compactPromotedVideoRenderer ||
                                            item.promotedVideoRenderer ||
                                            item.adPlacementRenderer ||
                                            item.companionAdRenderer ||
                                            item.inFeedAdLayoutRenderer ||
                                            item.statementBannerRenderer ||
                                            item.bannerPromoRenderer ||
                                            item.adRenderer) {
                                            return true;
                                        }
                                        // Check for sponsored badges inside compactVideoRenderer, videoWithContextRenderer, etc.
                                        const renderer = item.compactVideoRenderer || item.videoWithContextRenderer || item.videoRenderer;
                                        if (renderer && Array.isArray(renderer.badges)) {
                                            for (const badge of renderer.badges) {
                                                const mb = badge && (badge.metadataBadgeRenderer || badge);
                                                const label = (mb && (mb.label || mb.text || '')) + '';
                                                const style = (mb && mb.style) + '';
                                                if (style.includes('SPONSORED') || label.toLowerCase().includes('sponsored') || label.toLowerCase() === 'ad') {
                                                    return true;
                                                }
                                            }
                                        }
                                                    return false;
                                                }

                                                function stripAdsFromNextResponse(json) {
                                                    if (!json || typeof json !== 'object') return json;
                                                    try {
                                                        function cleanArray(arr) {
                                                            if (!Array.isArray(arr)) return;
                                                            for (let i = arr.length - 1; i >= 0; i--) {
                                                                const item = arr[i];
                                                                if (!item || typeof item !== 'object') continue;
                                                                if (isSponsoredItem(item)) {
                                                                    arr.splice(i, 1);
                                                                } else {
                                                                    cleanObject(item);
                                                                }
                                                            }
                                                        }
                                                        function cleanObject(obj) {
                                                            if (!obj || typeof obj !== 'object') return;
                                                            for (const key of Object.keys(obj)) {
                                                                if (Array.isArray(obj[key])) {
                                                                    cleanArray(obj[key]);
                                                                } else if (typeof obj[key] === 'object' && obj[key] !== null) {
                                                                    cleanObject(obj[key]);
                                                                }
                                                            }
                                                        }
                                                        cleanObject(json);
                                                    } catch (e) { }
                                                    return json;
                                                }

                                                // Intercept direct page variables if defined at document-start or inline scripts
                                                try {
                                                    let _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
                                                    Object.defineProperty(window, 'ytInitialPlayerResponse', {
                                                        get() { return _ytInitialPlayerResponse; },
                                                        set(val) {
                                                            _ytInitialPlayerResponse = stripAdsFromPlayerResponse(val);
                                                        },
                                                        configurable: true
                                                    });
                                                    if (_ytInitialPlayerResponse) {
                                                        _ytInitialPlayerResponse = stripAdsFromPlayerResponse(_ytInitialPlayerResponse);
                                                    }
                                                } catch (e) { }

                                            try {
                                                let _ytInitialData = window.ytInitialData;
                                                Object.defineProperty(window, 'ytInitialData', {
                                                    get() { return _ytInitialData; },
                                                    set(val) {
                                                        _ytInitialData = stripAdsFromNextResponse(val);
                                                    },
                                                    configurable: true
                                                });
                                                if (_ytInitialData) {
                                                    _ytInitialData = stripAdsFromNextResponse(_ytInitialData);
                                                }
                                            } catch (e) { }

                                                    // Intercept ytcfg player response on mobile
                                                    try {
                                                        if (window.ytcfg && typeof window.ytcfg.set === 'function') {
                                                            const origYtcfgSet = window.ytcfg.set;
                                                            window.ytcfg.set = function (key, val) {
                                                                if (key === 'PLAYER_VARS' && val && typeof val === 'object') {
                                                                    if (val.embedded_player_response) {
                                                                        try {
                                                                            const parsed = typeof val.embedded_player_response === 'string'
                                                                                ? JSON.parse(val.embedded_player_response)
                                                                                : val.embedded_player_response;
                                                                            stripAdsFromPlayerResponse(parsed);
                                                                            val.embedded_player_response = JSON.stringify(parsed);
                                                                        } catch (e) { }
                                                                    }
                                                                }
                                                                return origYtcfgSet.apply(this, arguments);
                                                            };
                                                        }
                                                    } catch (e) { }

                                                    const origFetch = window.fetch;
                                                    if (typeof origFetch === 'function') {
                                                        window.fetch = async function (...args) {
                                                            const rawUrl = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url ? args[0].url : '');
                                                            const response = await origFetch.apply(this, args);
                                                            if (rawUrl.includes('/youtubei/v1/player')) {
                                                                try {
                                                                    const clone = response.clone();
                                                                    const json = await clone.json();
                                                                    const cleaned = stripAdsFromPlayerResponse(json);
                                                                    const headers = new Headers(response.headers);
                                                                    headers.delete('content-encoding');
                                                                    headers.delete('content-length');
                                                                    headers.set('content-type', 'application/json; charset=UTF-8');
                                                                    return new Response(JSON.stringify(cleaned), {
                                                                        status: response.status,
                                                                        statusText: response.statusText,
                                                                        headers: headers
                                                                    });
                                                                } catch (e) {
                                                                    return response;
                                                                }
                                                            } else if (rawUrl.includes('/youtubei/v1/next')) {
                                                                try {
                                                                    const clone = response.clone();
                                                                    const json = await clone.json();
                                                                    const cleaned = stripAdsFromNextResponse(json);
                                                                    const headers = new Headers(response.headers);
                                                                    headers.delete('content-encoding');
                                                                    headers.delete('content-length');
                                                                    headers.set('content-type', 'application/json; charset=UTF-8');
                                                                    return new Response(JSON.stringify(cleaned), {
                                                                        status: response.status,
                                                                        statusText: response.statusText,
                                                                        headers: headers
                                                                    });
                                                                } catch (e) {
                                                                    return response;
                                                                }
                                                            }
                                                            return response;
                                                        };
                                                    }

                                                        const origXhrOpen = XMLHttpRequest.prototype.open;
                                                        const origXhrSend = XMLHttpRequest.prototype.send;
                                                        XMLHttpRequest.prototype.open = function (method, url, ...rest) {
                                                            this.__peach_url = typeof url === 'string' ? url : (url ? url.toString() : '');
                                                            return origXhrOpen.call(this, method, url, ...rest);
                                                        };

                                                    XMLHttpRequest.prototype.send = function (...args) {
                                                        if (this.__peach_url && (this.__peach_url.includes('/youtubei/v1/player') || this.__peach_url.includes('/youtubei/v1/next'))) {
                                                            const isPlayer = this.__peach_url.includes('/youtubei/v1/player');
                                                            const self = this;
                                                            let cleanedText = null;
                                                            let cleanedObj = null;

                                                                            function tryClean() {
                                                                                if (cleanedText !== null) return;
                                                                                try {
                                                                                    const raw = self.responseText;
                                                                                    if (raw && typeof raw === 'string') {
                                                                                        const json = JSON.parse(raw);
                                                                                        const cleaned = isPlayer ? stripAdsFromPlayerResponse(json) : stripAdsFromNextResponse(json);
                                                                                        cleanedObj = cleaned;
                                                                                        cleanedText = JSON.stringify(cleaned);
                                                                                    }
                                                                                } catch (e) { }
                                                                            }

                                                    try {
                                                        Object.defineProperty(this, 'responseText', {
                                                            get() {
                                                                tryClean();
                                                                return cleanedText !== null ? cleanedText : '';
                                                            },
                                                            configurable: true
                                                        });
                                                        Object.defineProperty(this, 'response', {
                                                            get() {
                                                                tryClean();
                                                                if (self.responseType === '' || self.responseType === 'text') {
                                                                    return cleanedText !== null ? cleanedText : '';
                                                                } else if (self.responseType === 'json') {
                                                                    return cleanedObj !== null ? cleanedObj : null;
                                                                }
                                                                return cleanedText;
                                                            },
                                                            configurable: true
                                                        });
                                                    } catch (e) { }
                                                }
                                                return origXhrSend.apply(this, args);
                                            };
                                        } catch (e) { }
                                    }

                                    setupYouTubePayloadInterceptor();

                                            // --- Real-Time Video Ad Fast-Skip Watchdog ---
                                            const SKIP_BUTTON_SELECTORS = [
                                                '.ytp-ad-skip-button',
                                                '.ytp-ad-skip-button-modern',
                                                '.ytp-skip-ad-button',
                                                '.videoAdUiSkipButton',
                                                '.ytp-ad-skip-button-slot button',
                                                '.ytp-ad-overlay-close-button',
                                                '.ytm-ad-skip-button',
                                                '.ytp-ad-skip-button-container button'
                                            ].join(', ');

                                            function fastSkipAd() {
                                                try {
                                                    if (window.__PEACH_CONFIG__ && window.__PEACH_CONFIG__.shieldActive === false) return;

                                                    const skipButtons = document.querySelectorAll(SKIP_BUTTON_SELECTORS);
                                                    skipButtons.forEach(btn => {
                                                        try { btn.click(); } catch (e) { }
                                                    });

                                                    const player = document.getElementById('movie_player') ||
                                                        document.querySelector('.html5-video-player') ||
                                                        document.querySelector('ytmusic-player') ||
                                                        document.querySelector('.player-container');

                                                            const isAdActive = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) ||
                                                                document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .ytp-ad-player-overlay-layout') !== null;

                                                            if (isAdActive) {
                                                                if (player && typeof player.skipAd === 'function') {
                                                                    try { player.skipAd(); } catch (e) { }
                                                                }
                                                                const videos = document.querySelectorAll('video');
                                                                videos.forEach(video => {
                                                                    try {
                                                                        video.muted = true;
                                                                        video.playbackRate = 16.0;
                                                                        if (isFinite(video.duration) && video.duration > 0) {
                                                                            video.currentTime = video.duration + 0.1;
                                                                        }
                                                                    } catch (e) { }
                                                                });
                                                            } else {
                                                                const videos = document.querySelectorAll('video');
                                                                videos.forEach(video => {
                                                                    try {
                                                                        if (video.playbackRate > 2.0) {
                                                                            video.playbackRate = 1.0;
                                                                        }
                                                                    } catch (e) { }
                                                                });
                                                            }
        } catch (e) { }
                                        }

                                        // Attach to video playback events for instant zero-delay trigger
                                        window.addEventListener('timeupdate', fastSkipAd, true);
                                        window.addEventListener('playing', fastSkipAd, true);
                                        window.addEventListener('loadedmetadata', fastSkipAd, true);

                                        // High-frequency polling watchdog
                                        window.setInterval(fastSkipAd, 150);
                                    }) ();
