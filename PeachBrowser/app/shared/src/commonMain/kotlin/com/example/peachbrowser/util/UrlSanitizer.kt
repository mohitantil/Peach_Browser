package com.example.peachbrowser.util

object UrlSanitizer {
    private val TRACKING_PARAMS = setOf(
        // Meta / Facebook / Instagram
        "fbclid", "igshid", "fb_action_ids", "fb_action_types", "fb_source",
        // Google Ads & Tracking
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
        // Universal Marketing & UTM
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_cid", "utm_reader", "utm_referrer", "utm_name",
        // Twitter / X
        "twclid", "ref_src", "ref_url",
        // Microsoft / Bing
        "msclkid",
        // Email marketing
        "mc_cid", "mc_eid", "_hsenc", "_hsmi",
        // YouTube, Spotify, TikTok
        "si", "spm", "_r", "_t", "tt_from",
        // Ad networks & Click IDs
        "yclid", "wickedid", "zanpid", "rb_clickid", "s_kwcid"
    )

    private val GOOGLE_AMP_REGEX = Regex(
        """^https?://(?:www\.)?google\.(?:[a-z]{2,3}(?:\.[a-z]{2})?)/amp/s/(.*)""",
        RegexOption.IGNORE_CASE
    )

    private val IDENTITY_PROVIDER_DOMAINS = setOf(
        "accounts.google.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com",
        "auth.openai.com",
        "auth0.com",
        "okta.com",
        "clerk.com",
        "clerk.accounts.dev"
    )

    data class ParsedUrl(
        val host: String,
        val path: String,
        val queryParams: List<String>
    )

    fun parseUrl(url: String): ParsedUrl? {
        try {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                return null
            }
            val withoutScheme = trimmed.substringAfter("://")
            val authority = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            val hostPart = authority.substringAfterLast('@').substringBefore(':').trim().lowercase()
            if (hostPart.isBlank()) return null

            val rest = withoutScheme.substring(authority.length)
            val pathPart = if (rest.startsWith('/')) {
                "/" + rest.substringBefore('?').substringBefore('#').trimStart('/')
            } else {
                "/"
            }

            val queryPart = if (trimmed.contains('?')) {
                trimmed.substringAfter('?').substringBefore('#')
            } else {
                ""
            }
            val queryParams = if (queryPart.isNotBlank()) queryPart.split('&') else emptyList()

            return ParsedUrl(host = hostPart, path = pathPart.lowercase(), queryParams = queryParams)
        } catch (_: Exception) {
            return null
        }
    }

    fun isAuthOrOAuthUrl(url: String): Boolean {
        val parsed = parseUrl(url) ?: return false
        val host = parsed.host

        // 1. Identity Provider Host Match (exact or subdomain)
        if (IDENTITY_PROVIDER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            return true
        }

        val queryKeys = parsed.queryParams.map { it.substringBefore('=').trim().lowercase() }.toSet()

        // 2. RFC 6749 Section 4.1.1: Authorization Request
        if (queryKeys.contains("client_id") && (queryKeys.contains("response_type") || queryKeys.contains("redirect_uri"))) {
            return true
        }

        // 3. RFC 6749 Section 4.1.2 & 4.1.2.1: Authorization Response (Success or Error)
        val hasAuthToken = queryKeys.contains("code") || queryKeys.contains("error")
        if (hasAuthToken && queryKeys.contains("state")) {
            return true
        }
        val path = parsed.path
        val isCallbackPath = path.contains("/callback") || 
                             path.contains("/oauth2/code/") || 
                             path.contains("/auth/callback") ||
                             path.endsWith("/callback")
        if (hasAuthToken && isCallbackPath) {
            return true
        }

        // 4. Known multi-purpose identity endpoints
        if (host == "github.com" && path.startsWith("/login/oauth")) {
            return true
        }

        return false
    }

    /**
     * Bypasses Google AMP cache redirects to extract the canonical publisher URL.
     */
    fun deAmp(url: String): String {
        val trimmed = url.trim()
        if (isAuthOrOAuthUrl(trimmed)) return url
        val match = GOOGLE_AMP_REGEX.find(trimmed) ?: return trimmed
        val canonical = match.groupValues[1]
        return if (!canonical.startsWith("http://", ignoreCase = true) && !canonical.startsWith("https://", ignoreCase = true)) {
            "https://$canonical"
        } else {
            canonical
        }
    }

    /**
     * Strips intrusive cross-site query parameters while preserving functional ones and OAuth/SSO handshakes.
     */
    fun stripTrackingParams(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true))) {
            return url
        }
        if (isAuthOrOAuthUrl(trimmed)) {
            return url
        }

        val hashIndex = trimmed.indexOf('#')
        val fragment = if (hashIndex != -1) trimmed.substring(hashIndex) else ""
        val urlWithoutFragment = if (hashIndex != -1) trimmed.substring(0, hashIndex) else trimmed

        val queryIndex = urlWithoutFragment.indexOf('?')
        if (queryIndex == -1) return url

        val basePath = urlWithoutFragment.substring(0, queryIndex)
        val rawQuery = urlWithoutFragment.substring(queryIndex + 1)
        if (rawQuery.isBlank()) return url

        val allPairs = rawQuery.split('&')
        val keptParams = allPairs.filter { pair ->
            val key = pair.substringBefore('=').trim().lowercase()
            key.isNotBlank() && !isTrackingParam(key)
        }

        return when {
            keptParams.size == allPairs.size -> url
            keptParams.isEmpty() -> "$basePath$fragment"
            else -> "$basePath?${keptParams.joinToString("&")}$fragment"
        }
    }

    private fun isTrackingParam(param: String): Boolean {
        if (TRACKING_PARAMS.contains(param)) return true
        if (param.startsWith("utm_")) return true
        return false
    }

    fun sanitize(url: String): String = stripTrackingParams(deAmp(url))
}
