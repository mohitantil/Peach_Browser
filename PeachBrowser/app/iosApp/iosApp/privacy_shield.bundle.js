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

    const COSMETIC_STYLES = `
        ins.adsbygoogle,
            [id ^= "google_ads_iframe"],
            [id ^= "div-gpt-ad"],
            iframe[src *= "doubleclick.net"],
            iframe[src *= "googlesyndication.com"],
            iframe[src *= "googleadservices.com"],
        .trc_related_container,
        .OUTBRAIN,
            [data - widget - type="taboola"],
            [id ^= "taboola-"],
            ytd - ad - slot - renderer,
            ytd -in -feed - ad - layout - renderer,
            ytd - action - companion - ad - renderer,
            ytd - promoted - sparkles - web - renderer,
            ytd - display - ad - renderer,
            ytd - promoted - video - renderer,
            ytd - compact - promoted - video - renderer,
            ytd - banner - promo - renderer,
            ytd - statement - banner - renderer,
            ytd - mealbar - promo - renderer,
            ytmusic - mealbar - promo - renderer,
            #masthead - ad,
            #player - ads,
        .ytp - ad - overlay - container,
        .ytp - ad - overlay - slot,
            ytd - enforcement - message - view - model {
            display: none!important;
        }
        `;

    function injectCosmeticCSS() {
        try {
            if (document.getElementById('peach-shield-css')) return true;
            if (document.head) {
                const styleEl = document.createElement('style');
                styleEl.id = 'peach-shield-css';
                styleEl.textContent = COSMETIC_STYLES;
                document.head.appendChild(styleEl);
                return true;
            }
        } catch (e) {}
        return false;
    }

    if (!injectCosmeticCSS()) {
        document.addEventListener('DOMContentLoaded', injectCosmeticCSS, { once: true });
    }

    const isYouTubeDomain = (
        currentHost === 'youtube.com' ||
        currentHost === 'www.youtube.com' ||
        currentHost === 'm.youtube.com' ||
        currentHost === 'music.youtube.com'
    );

    if (!isYouTubeDomain) return;

    const SKIP_BUTTON_SELECTORS = [
        '.ytp-ad-skip-button',
        '.ytp-ad-skip-button-modern',
        '.ytp-skip-ad-button',
        '.videoAdUiSkipButton',
        '.ytp-ad-skip-button-slot button',
        '.ytp-ad-overlay-close-button'
    ].join(', ');

    function neutralizeYouTubeAds() {
        try {
            if (window.__PEACH_CONFIG__ && window.__PEACH_CONFIG__.shieldActive === false) return;

            const skipButtons = document.querySelectorAll(SKIP_BUTTON_SELECTORS);
            skipButtons.forEach(btn => {
                try { btn.click(); } catch (e) {}
            });

            const player = document.getElementById('movie_player') ||
                document.querySelector('.html5-video-player') ||
                document.querySelector('ytmusic-player');
            if (!player) return;

            const isAdShowing = player.classList.contains('ad-showing') ||
                player.classList.contains('ad-interrupting');

            if (isAdShowing) {
                if (typeof player.skipAd === 'function') {
                    try { player.skipAd(); } catch (e) {}
                }
                const adVideo = player.querySelector('video.html5-main-video') || player.querySelector('video');
                if (adVideo && isFinite(adVideo.duration) && adVideo.duration > 0) {
                    adVideo.muted = true;
                    adVideo.currentTime = adVideo.duration;
                }
            }
        } catch (e) {}
    }

    window.setInterval(neutralizeYouTubeAds, 600);
})();
