/**
 * Peach AI Browser — Privacy Shield & Ad Neutralizer Engine (v1.0.2 Restoration)
 */
(function initPeachShield() {
    'use strict';

    if (window.__PEACH_SHIELD_ACTIVE__) {
        return;
    }

    const currentHost = (location.hostname || '').toLowerCase();
    if (!currentHost) {
        return;
    }

    const defaultExceptionDomains = [
        'chatgpt.com', 'openai.com', 'oaistatic.com', 'oaiusercontent.com',
        'claude.ai', 'anthropic.com',
        'gemini.google.com', 'aistudio.google.com',
        'gstatic.com', 'googleapis.com',
        'qwen.ai', 'qwenlm.ai', 'aliyun.com', 'alicdn.com',
        'z.ai', 'chat.z.ai',
        'deepseek.com', 'openrouter.ai', 'huggingface.co'
    ];

    let isExceptionSite = defaultExceptionDomains.some(d => currentHost === d || currentHost.endsWith('.' + d));

    try {
        if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isSiteException === 'function') {
            if (window.PeachNativeBridge.isSiteException(currentHost)) {
                isExceptionSite = true;
            }
        } else if (window.__PEACH_CONFIG__ && window.__PEACH_CONFIG__.isSiteException === true) {
            isExceptionSite = true;
        }
    } catch (e) {}

    if (isExceptionSite) {
        return;
    }

    try {
        if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isShieldActive === 'function') {
            if (!window.PeachNativeBridge.isShieldActive(currentHost)) {
                return;
            }
        } else if (window.__PEACH_CONFIG__ && typeof window.__PEACH_CONFIG__.shieldActive === 'boolean') {
            if (!window.__PEACH_CONFIG__.shieldActive) {
                return;
            }
        }
    } catch (e) {}

    window.__PEACH_SHIELD_ACTIVE__ = true;

    const COSMETIC_STYLES = `
        ins.adsbygoogle,
        [id^="google_ads"],
        [id^="div-gpt-ad"],
        .ad-container,
        .ad-wrapper,
        .ad-slot,
        .ad-banner,
        .ad-unit,
        .adsbygoogle,
        [data-ad-slot],
        iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"],
        iframe[src*="googleadservices"],
        .trc_related_container,
        .OUTBRAIN,
        [data-widget-type="taboola"],
        [id^="taboola-"],
        .taboola,
        .outbrain,
        .sponsored-ad,
        .Sponsored-ad,
        [data-testid="sponsored-ad"],
        ytd-ad-slot-renderer,
        ytd-in-feed-ad-layout-renderer,
        ytd-action-companion-ad-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-display-ad-renderer,
        ytd-promoted-video-renderer,
        ytd-compact-promoted-video-renderer,
        ytd-banner-promo-renderer,
        ytd-statement-banner-renderer,
        ytd-mealbar-promo-renderer,
        ytmusic-mealbar-promo-renderer,
        .ytd-merch-shelf-renderer,
        #masthead-ad,
        #player-ads,
        #panels .ytd-ads-engagement-panel-content-renderer,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .ytp-ad-overlay-slot,
        .video-ads,
        .ytp-ad-player-overlay,
        .ytp-ad-player-overlay-layout,
        .ytp-ad-player-overlay-flyout-cta,
        .ytp-ad-text,
        .ytp-ad-preview-text,
        ytd-enforcement-message-view-model {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            opacity: 0 !important;
            overflow: hidden !important;
            pointer-events: none !important;
        }
    `;

    function injectCosmeticCSS() {
        try {
            if (document.getElementById('peach-shield-css')) {
                return true;
            }
            const target = document.head || document.documentElement;
            if (target) {
                const styleEl = document.createElement('style');
                styleEl.id = 'peach-shield-css';
                styleEl.textContent = COSMETIC_STYLES;
                target.appendChild(styleEl);
                return true;
            }
        } catch (e) {}
        return false;
    }

    if (!injectCosmeticCSS()) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => injectCosmeticCSS());
        }
        const styleInterval = window.setInterval(() => {
            if (injectCosmeticCSS()) {
                clearInterval(styleInterval);
            }
        }, 100);
    }

    const isYouTubeDomain = (
        currentHost === 'youtube.com' ||
        currentHost === 'www.youtube.com' ||
        currentHost === 'm.youtube.com' ||
        currentHost === 'music.youtube.com'
    );

    if (!isYouTubeDomain) {
        return;
    }

    const SKIP_BUTTON_SELECTORS = [
        '.ytp-ad-skip-button',
        '.ytp-ad-skip-button-modern',
        '.ytp-skip-ad-button',
        '.videoAdUiSkipButton',
        '.ytp-ad-skip-button-slot button',
        '.ytp-ad-overlay-close-button'
    ].join(', ');

    const COMPANION_AD_SELECTORS = [
        'ytd-ad-slot-renderer',
        'ytd-in-feed-ad-layout-renderer',
        'ytd-banner-promo-renderer',
        'ytmusic-mealbar-promo-renderer',
        '#player-ads',
        '#masthead-ad'
    ].join(', ');

    function neutralizeYouTubeAds() {
        try {
            const skipButtons = document.querySelectorAll(SKIP_BUTTON_SELECTORS);
            skipButtons.forEach(btn => {
                try { btn.click(); } catch (e) {}
            });

            const companionAds = document.querySelectorAll(COMPANION_AD_SELECTORS);
            companionAds.forEach(node => {
                try { node.remove(); } catch (e) {}
            });

            const player = document.getElementById('movie_player') ||
                           document.querySelector('.html5-video-player') ||
                           document.querySelector('ytmusic-player');
            if (!player) return;

            const isAdShowing = player.classList.contains('ad-showing') ||
                                player.classList.contains('ad-interrupting') ||
                                (typeof player.getAdState === 'function' && player.getAdState() === 1) ||
                                document.querySelector('.ad-showing, .ad-interrupting') !== null;

            if (isAdShowing) {
                if (typeof player.skipAd === 'function') {
                    try { player.skipAd(); } catch (e) {}
                }

                const adVideo = player.querySelector('video.html5-main-video') ||
                                player.querySelector('video') ||
                                document.querySelector('video');
                if (adVideo) {
                    try {
                        adVideo.muted = true;
                        adVideo.playbackRate = 16.0;
                        if (isFinite(adVideo.duration) && adVideo.duration > 0) {
                            adVideo.currentTime = adVideo.duration;
                        }
                        adVideo.dispatchEvent(new Event('ended'));
                    } catch (e) {}
                }
            }
        } catch (e) {}
    }

    const attachObserver = () => {
        if (!document.documentElement) return;
        try {
            const observer = new MutationObserver(() => {
                neutralizeYouTubeAds();
            });
            observer.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'src']
            });
        } catch (e) {}
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', attachObserver);
    } else {
        attachObserver();
    }

    window.setInterval(neutralizeYouTubeAds, 250);
})();
