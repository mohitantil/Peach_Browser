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

    private val GOOGLE_AMP_REGEX = Regex("""^https?://(?:www\.)?google\.[a-z.]+/amp/s/(.*)""", RegexOption.IGNORE_CASE)

    /**
     * Bypasses Google AMP cache redirects to extract the canonical publisher URL.
     */
    fun deAmp(url: String): String {
        val trimmed = url.trim()
        val match = GOOGLE_AMP_REGEX.find(trimmed) ?: return trimmed
        val canonical = match.groupValues[1]
        return if (!canonical.startsWith("http://") && !canonical.startsWith("https://")) {
            "https://$canonical"
        } else {
            canonical
        }
    }

    /**
     * Strips intrusive cross-site query parameters while preserving functional ones.
     */
    fun stripTrackingParams(url: String): String {
        val trimmed = url.trim()
        val queryStartIndex = trimmed.indexOf('?')
        if (queryStartIndex == -1) return trimmed

        val baseUrl = trimmed.substring(0, queryStartIndex)
        val queryAndHash = trimmed.substring(queryStartIndex + 1)
        val hashStartIndex = queryAndHash.indexOf('#')

        val queryString = if (hashStartIndex != -1) queryAndHash.substring(0, hashStartIndex) else queryAndHash
        val hash = if (hashStartIndex != -1) queryAndHash.substring(hashStartIndex) else ""

        val rawParams = queryString.split('&')
        val cleanParams = mutableListOf<String>()

        for (param in rawParams) {
            if (param.isBlank()) continue
            val key = param.substringBefore('=').trim().lowercase()
            if (!TRACKING_PARAMS.contains(key)) {
                cleanParams.add(param)
            }
        }

        return buildString {
            append(baseUrl)
            if (cleanParams.isNotEmpty()) {
                append('?')
                append(cleanParams.joinToString("&"))
            }
            append(hash)
        }
    }

    /**
     * Executes both De-AMPing and Query String Filtering in a single pass.
     */
    fun sanitize(url: String): String {
        val deAmped = deAmp(url)
        return stripTrackingParams(deAmped)
    }
}
