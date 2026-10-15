/**
 * Peach AI Browser — Advanced Farbling & Anti-Fingerprinting Engine
 * Protects against Canvas, WebGL, AudioContext, and Hardware fingerprinting
 * by introducing subtle, deterministic pseudo-random noise per origin & session.
 * Exposes Global Privacy Control (GPC) and Do Not Track (DNT) signals.
 */
(function initPeachFarbler() {
    'use strict';

    if (window.__PEACH_FARBLER_ACTIVE__) {
        return;
    }

    let currentHost = '';
    try {
        currentHost = (location.hostname || '').toLowerCase();
        if (!currentHost && window.top && window.top.location) {
            currentHost = (window.top.location.hostname || '').toLowerCase();
        }
    } catch (e) {}

    if (!currentHost) {
        return;
    }

    const spaAllowlist = [
        'chatgpt.com', 'openai.com', 'oaistatic.com', 'oaiusercontent.com',
        'qwen.ai', 'qwenlm.ai', 'aliyun.com', 'alicdn.com',
        'z.ai', 'chat.z.ai', 'deepseek.com', 'openrouter.ai',
        'gemini.google.com', 'aistudio.google.com', 'music.youtube.com', 'youtube.com', 'google.com',
        'claude.ai', 'anthropic.com', 'cloudflare.com', 'challenges.cloudflare.com'
    ];

    if (spaAllowlist.some(d => currentHost === d || currentHost.endsWith('.' + d))) {
        return;
    }

    try {
        if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isSiteException === 'function') {
            if (window.PeachNativeBridge.isSiteException(currentHost)) {
                return;
            }
        } else if (window.__PEACH_CONFIG__ && window.__PEACH_CONFIG__.isSiteException === true) {
            return;
        }
    } catch (e) {}

    try {
        if (window.PeachNativeBridge && typeof window.PeachNativeBridge.isFarblingActive === 'function') {
            if (!window.PeachNativeBridge.isFarblingActive(currentHost)) {
                return;
            }
        } else if (window.__PEACH_CONFIG__ && typeof window.__PEACH_CONFIG__.farblingActive === 'boolean') {
            if (!window.__PEACH_CONFIG__.farblingActive) {
                return;
            }
        }
    } catch (e) {}

    window.__PEACH_FARBLER_ACTIVE__ = true;

    function maskNativeFunction(fn, nativeName) {
        try {
            Object.defineProperty(fn, 'name', { value: nativeName, configurable: true });
            Object.defineProperty(fn, 'toString', {
                value: function() { return 'function ' + nativeName + '() { [native code] }'; },
                configurable: true,
                writable: true
            });
        } catch (e) {}
        return fn;
    }

    try {
        // 1. Global Privacy Control & DNT Signals
        try {
            Object.defineProperty(navigator, 'globalPrivacyControl', {
                get: maskNativeFunction(() => true, 'get globalPrivacyControl'),
                configurable: true,
                enumerable: true
            });
            Object.defineProperty(navigator, 'doNotTrack', {
                get: maskNativeFunction(() => '1', 'get doNotTrack'),
                configurable: true,
                enumerable: true
            });
        } catch (e) {}

        // 2. Hardware Entropy Reduction
        try {
            Object.defineProperty(navigator, 'hardwareConcurrency', {
                get: maskNativeFunction(() => 8, 'get hardwareConcurrency'),
                configurable: true,
                enumerable: true
            });
            if ('deviceMemory' in navigator) {
                Object.defineProperty(navigator, 'deviceMemory', {
                    get: maskNativeFunction(() => 8, 'get deviceMemory'),
                    configurable: true,
                    enumerable: true
                });
            }
        } catch (e) {}

        // 3. Deterministic Origin Seed Generator
        let seed = 0;
        for (let i = 0; i < currentHost.length; i++) {
            seed = ((seed << 5) - seed) + currentHost.charCodeAt(i);
            seed |= 0;
        }

        const pseudoRandom = () => {
            seed = (Math.abs(seed * 9301 + 49297)) % 233280;
            return seed / 233280;
        };

        // 4. Non-Destructive Canvas Farbler (small fingerprinting probes <= 64x64 only)
        if (typeof CanvasRenderingContext2D !== 'undefined' && CanvasRenderingContext2D.prototype.getImageData) {
            const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
            const farbledGetImageData = function(x, y, w, h) {
                const imageData = origGetImageData.apply(this, arguments);
                try {
                    if (imageData && imageData.data && imageData.data.length > 16) {
                        const canvasW = this.canvas ? this.canvas.width : w;
                        const canvasH = this.canvas ? this.canvas.height : h;
                        if (canvasW > 0 && canvasW <= 64 && canvasH > 0 && canvasH <= 64) {
                            const pixelIndex = Math.floor(pseudoRandom() * (imageData.data.length / 4)) * 4;
                            if (pixelIndex >= 0 && pixelIndex < imageData.data.length) {
                                imageData.data[pixelIndex] = imageData.data[pixelIndex] ^ 1;
                            }
                        }
                    }
                } catch (e) {}
                return imageData;
            };
            CanvasRenderingContext2D.prototype.getImageData = maskNativeFunction(farbledGetImageData, 'getImageData');
        }

        // 5. AudioContext Farbler
        if (typeof AudioBuffer !== 'undefined' && AudioBuffer.prototype.getChannelData) {
            const origGetChannelData = AudioBuffer.prototype.getChannelData;
            const farbledGetChannelData = function(channel) {
                const data = origGetChannelData.apply(this, arguments);
                try {
                    if (data && data.length > 10 && data.length <= 8192) {
                        const idx = Math.floor(pseudoRandom() * data.length);
                        if (idx >= 0 && idx < data.length) {
                            data[idx] += (pseudoRandom() - 0.5) * 0.0000001;
                        }
                    }
                } catch (e) {}
                return data;
            };
            AudioBuffer.prototype.getChannelData = maskNativeFunction(farbledGetChannelData, 'getChannelData');
        }

        // 6. WebGL Farbler with valid mobile GPU strings
        const hookWebGL = (proto) => {
            if (!proto || !proto.getParameter) return;
            const origGetParameter = proto.getParameter;
            const farbledGetParameter = function(pname) {
                try {
                    if (pname === 0x9245) {
                        return 'Qualcomm';
                    }
                    if (pname === 0x9246) {
                        return 'Adreno (TM) 740';
                    }
                } catch (e) {}
                return origGetParameter.apply(this, arguments);
            };
            proto.getParameter = maskNativeFunction(farbledGetParameter, 'getParameter');
        };

        if (typeof WebGLRenderingContext !== 'undefined') {
            hookWebGL(WebGLRenderingContext.prototype);
        }
        if (typeof WebGL2RenderingContext !== 'undefined') {
            hookWebGL(WebGL2RenderingContext.prototype);
        }
    } catch (e) {}
})();
