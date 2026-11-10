/**
 * Peach AI Browser — Non-Destructive Farbling & Anti-Fingerprinting Engine (Nov 2026)
 */
(function initPeachFarbler() {
    'use strict';

    if (window.__PEACH_FARBLER_ACTIVE__) return;

    let currentHost = '';
    try {
        currentHost = (location.hostname || '').toLowerCase();
        if (!currentHost && window.top && window.top.location) {
            currentHost = (window.top.location.hostname || '').toLowerCase();
        }
    } catch (e) { }

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
        const sessionEntropy = (typeof window.__PEACH_SESSION_SALT__ === 'number') ? window.__PEACH_SESSION_SALT__ : Math.floor(Math.random() * 1000000);
        let seed = sessionEntropy;
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
                                const canvasW = thisArg && thisArg.canvas ? thisArg.canvas.width : (argArray[2] || 0);
                                const canvasH = thisArg && thisArg.canvas ? thisArg.canvas.height : (argArray[3] || 0);
                                if (canvasW > 0 && canvasW <= 1024 && canvasH > 0 && canvasH <= 1024) {
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

        if (typeof HTMLCanvasElement !== 'undefined' && HTMLCanvasElement.prototype.toDataURL) {
            const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = new Proxy(origToDataURL, {
                apply(target, thisArg, argArray) {
                    try {
                        const ctx = thisArg.getContext && thisArg.getContext('2d');
                        if (ctx && thisArg.width > 0 && thisArg.width <= 1024 && thisArg.height > 0 && thisArg.height <= 1024) {
                            const img = ctx.getImageData(0, 0, 1, 1);
                            if (img && img.data && img.data.length >= 4) {
                                img.data[0] = img.data[0] ^ 1;
                                ctx.putImageData(img, 0, 0);
                            }
                        }
                    } catch (e) { }
                    return Reflect.apply(target, thisArg, argArray);
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
})();
