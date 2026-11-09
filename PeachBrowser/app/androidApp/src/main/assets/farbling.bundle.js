/**
 * Peach AI Browser — Non-Destructive Farbling & Anti-Fingerprinting Engine (Nov 2026)
 */
(function initPeachFarbler() {
    'use strict';

    if (window.__PEACH_FARBLER_ACTIVE__) return;
    if (window !== window.top) return;

    const currentHost = (location.hostname || '').toLowerCase();
    if (!currentHost) return;

    const spaAllowlist = [
        'chatgpt.com', 'openai.com', 'auth.openai.com', 'oaistatic.com', 'oaiusercontent.com',
        'qwen.ai', 'qwenlm.ai', 'aliyun.com', 'alicdn.com',
        'z.ai', 'chat.z.ai', 'deepseek.com', 'openrouter.ai',
        'gemini.google.com', 'aistudio.google.com', 'music.youtube.com', 'youtube.com', 'google.com',
        'accounts.google.com', 'appleid.apple.com', 'login.microsoftonline.com', 'login.live.com',
        'github.com', 'auth0.com', 'clerk.com', 'clerk.accounts.dev',
        'claude.ai', 'anthropic.com', 'cloudflare.com', 'challenges.cloudflare.com'
    ];

    if (spaAllowlist.some(d => currentHost === d || currentHost.endsWith('.' + d))) {
        return;
    }

        try {
            if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isFarblingActive === 'function') {
                if (!window.PeachNativeBridge.isFarblingActive(currentHost)) {
                    return;
                }
            } else if (window.__PEACH_CONFIG__) {
                if (window.__PEACH_CONFIG__.isSiteException === true || window.__PEACH_CONFIG__.farblingActive === false) {
                    return;
                }
            }
        } catch (e) { }

        window.__PEACH_FARBLER_ACTIVE__ = true;

            try {
                let seed = 0;
                for (let i = 0; i < currentHost.length; i++) {
                    seed = ((seed << 5) - seed) + currentHost.charCodeAt(i);
                    seed |= 0;
                }

                const pseudoRandom = () => {
                    seed = (Math.abs(seed * 9301 + 49297)) % 233280;
                    return seed / 233280;
                };

                            if (typeof CanvasRenderingContext2D !== 'undefined' && CanvasRenderingContext2D.prototype.getImageData) {
                                const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                                CanvasRenderingContext2D.prototype.getImageData = new Proxy(origGetImageData, {
                                    apply(target, thisArg, argArray) {
                                        const imageData = Reflect.apply(target, thisArg, argArray);
                                        try {
                                            if (imageData && imageData.data && imageData.data.length > 16) {
                                                const canvasW = thisArg && thisArg.canvas ? thisArg.canvas.width : argArray[2];
                                                const canvasH = thisArg && thisArg.canvas ? thisArg.canvas.height : argArray[3];
                                                if (canvasW > 0 && canvasW <= 64 && canvasH > 0 && canvasH <= 64) {
                                                    const pixelIndex = Math.floor(pseudoRandom() * (imageData.data.length / 4)) * 4;
                                                    if (pixelIndex >= 0 && pixelIndex < imageData.data.length) {
                                                        imageData.data[pixelIndex] = imageData.data[pixelIndex] ^ 1;
                                                    }
                                                }
                        }
                    } catch (e) { }
                    return imageData;
                }
            });
        }

        if (typeof AudioBuffer !== 'undefined' && AudioBuffer.prototype.getChannelData) {
            const origGetChannelData = AudioBuffer.prototype.getChannelData;
            AudioBuffer.prototype.getChannelData = new Proxy(origGetChannelData, {
                apply(target, thisArg, argArray) {
                    const data = Reflect.apply(target, thisArg, argArray);
                    try {
                        if (data && data.length > 10 && data.length <= 8192) {
                            const idx = Math.floor(pseudoRandom() * data.length);
                            if (idx >= 0 && idx < data.length) {
                                data[idx] += (pseudoRandom() - 0.5) * 0.0000001;
                            }
                        }
        } catch (e) { }
        return data;
    }
});
        }
    } catch (e) { }
}) ();
