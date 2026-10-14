/**
 * Peach AI Browser — Privacy Shield & Ad Neutralizer Engine
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

    const allowlistedDomains = [
        'chatgpt.com', 'openai.com', 'oaistatic.com', 'oaiusercontent.com',
        'claude.ai', 'anthropic.com',
        'gemini.google.com', 'aistudio.google.com',
        'music.youtube.com',
        'gstatic.com', 'googleapis.com',
        'qwen.ai', 'qwenlm.ai', 'aliyun.com', 'alicdn.com',
        'z.ai', 'chat.z.ai',
        'deepseek.com', 'huggingface.co'
    ];
    const isAllowlisted = allowlistedDomains.some(d => currentHost === d || currentHost.endsWith('.' + d));

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
        if (isAllowlisted) {
            return true;
        }
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

    const isStandardYouTube = (currentHost === 'youtube.com' || currentHost === 'www.youtube.com' || currentHost === 'm.youtube.com');
    if (!isStandardYouTube) {
        return;
    }

    function safeSkipYouTubeAds() {
        try {
            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (!player) return;

            const isAdShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
            if (isAdShowing) {
                if (typeof player.skipAd === 'function') {
                    try { player.skipAd(); } catch (e) {}
                }

                const skipBtn = player.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot button');
                if (skipBtn) {
                    try {
                        skipBtn.click();
                    } catch (e) {}
                }

                const adVideo = player.querySelector('video.html5-main-video') || player.querySelector('video');
                if (adVideo && isFinite(adVideo.duration) && adVideo.duration > 0) {
                    try {
                        adVideo.muted = true;
                        adVideo.currentTime = adVideo.duration;
                    } catch (e) {}
                }
            }
        } catch (e) {}
    }

    window.setInterval(safeSkipYouTubeAds, 500);
})();
