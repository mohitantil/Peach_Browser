/**
 * Pikachu AI Browser — Brave-Grade Farbling & Anti-Fingerprinting Engine
 * Protects against Canvas, WebGL, AudioContext, and Hardware fingerprinting
 * by introducing subtle, deterministic pseudo-random noise per origin & session.
 * Exposes Global Privacy Control (GPC) and Do Not Track (DNT) signals.
 */
(function initPikachuFarbler() {
    'use strict';

    if (window.__PIKACHU_FARBLER_ACTIVE__) {
        return;
    }
    window.__PIKACHU_FARBLER_ACTIVE__ = true;

    try {
        // ─── 1. GLOBAL PRIVACY CONTROL & DNT SIGNALS ─────────────────────────────
        try {
            Object.defineProperty(navigator, 'globalPrivacyControl', {
                get: () => true,
                configurable: false,
                enumerable: true
            });
            Object.defineProperty(navigator, 'doNotTrack', {
                get: () => '1',
                configurable: false,
                enumerable: true
            });
            Object.defineProperty(window, 'doNotTrack', {
                get: () => '1',
                configurable: false,
                enumerable: true
            });
        } catch (e) {}

        // ─── 2. HARDWARE ENTROPY REDUCTION ────────────────────────────────────────
        try {
            Object.defineProperty(navigator, 'hardwareConcurrency', {
                get: () => 8,
                configurable: true,
                enumerable: true
            });
            Object.defineProperty(navigator, 'deviceMemory', {
                get: () => 8,
                configurable: true,
                enumerable: true
            });
            if ('getBattery' in navigator) {
                delete navigator.getBattery;
            }
        } catch (e) {}

        // ─── 3. DETERMINISTIC ORIGIN SEED GENERATOR ──────────────────────────────
        const originStr = window.location.host || 'pikachu.browser';
        let seed = 0;
        for (let i = 0; i < originStr.length; i++) {
            seed = ((seed << 5) - seed) + originStr.charCodeAt(i);
            seed |= 0;
        }

        // Pseudo-random generator with origin seed
        const pseudoRandom = () => {
            seed = (seed * 9301 + 49297) % 233280;
            return seed / 233280;
        };

        // ─── 4. CANVAS FINGERPRINTING FARBLER ──────────────────────────────────────
        const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
        CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
            const imageData = origGetImageData.apply(this, arguments);
            try {
                if (imageData && imageData.data && imageData.data.length > 4) {
                    // Farble only 1-2 random pixel channels by 1 bit so human cannot notice
                    const pixelIndex = Math.floor(pseudoRandom() * (imageData.data.length / 4)) * 4;
                    imageData.data[pixelIndex] ^= 1;
                }
            } catch (e) {}
            return imageData;
        };

        const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
        HTMLCanvasElement.prototype.toDataURL = function() {
            try {
                const ctx = this.getContext('2d');
                if (ctx && this.width > 0 && this.height > 0) {
                    // Touch 1 single pixel with zero-opacity farble
                    const origFill = ctx.fillStyle;
                    ctx.fillStyle = 'rgba(255, 255, 255, 0.005)';
                    ctx.fillRect(0, 0, 1, 1);
                    ctx.fillStyle = origFill;
                }
            } catch (e) {}
            return origToDataURL.apply(this, arguments);
        };

        // ─── 5. AUDIOCONTEXT FINGERPRINTING FARBLER ────────────────────────────────
        if (typeof AudioBuffer !== 'undefined') {
            const origGetChannelData = AudioBuffer.prototype.getChannelData;
            AudioBuffer.prototype.getChannelData = function(channel) {
                const data = origGetChannelData.apply(this, arguments);
                try {
                    if (data && data.length > 10) {
                        // Inject 10^-7 microscopic Gaussian noise into 2 samples
                        const idx = Math.floor(pseudoRandom() * data.length);
                        data[idx] += (pseudoRandom() - 0.5) * 0.0000001;
                    }
                } catch (e) {}
                return data;
            };
        }

        if (typeof AnalyserNode !== 'undefined') {
            const origGetFloatFrequencyData = AnalyserNode.prototype.getFloatFrequencyData;
            AnalyserNode.prototype.getFloatFrequencyData = function(array) {
                origGetFloatFrequencyData.apply(this, arguments);
                try {
                    if (array && array.length > 0) {
                        const idx = Math.floor(pseudoRandom() * array.length);
                        array[idx] += (pseudoRandom() - 0.5) * 0.00001;
                    }
                } catch (e) {}
            };
        }

        // ─── 6. WEBGL FINGERPRINTING FARBLER ──────────────────────────────────────
        const hookWebGL = (proto) => {
            if (!proto) return;
            const origGetParameter = proto.getParameter;
            proto.getParameter = function(pname) {
                // UNMASKED_VENDOR_WEBGL
                if (pname === 0x9245) {
                    return 'Google Inc. (Pikachu Browser)';
                }
                // UNMASKED_RENDERER_WEBGL
                if (pname === 0x9246) {
                    return 'ANGLE (Generic GPU, Direct3D11/Vulkan/Metal, Generic Driver)';
                }
                return origGetParameter.apply(this, arguments);
            };
        };

        if (typeof WebGLRenderingContext !== 'undefined') {
            hookWebGL(WebGLRenderingContext.prototype);
        }
        if (typeof WebGL2RenderingContext !== 'undefined') {
            hookWebGL(WebGL2RenderingContext.prototype);
        }

        console.log('[Pikachu] Farbling and Global Privacy Control shield active.');
    } catch (e) {
        console.error('[Pikachu] Farbling initialization exception:', e);
    }
})();
