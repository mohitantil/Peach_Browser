package com.example.peachbrowser.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlSanitizerTest {

    @Test
    fun testDedicatedIdpMatching() {
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://accounts.google.com/o/oauth2/v2/auth?prompt=select_account"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://appleid.apple.com/auth/authorize"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://login.live.com/oauth20_authorize.srf"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://auth.openai.com/oauth/authorize"))
    }

    @Test
    fun testMultiTenantIdpApexAndSubdomains() {
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://auth0.com/auth"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://mytenant.us.auth0.com/authorize?response_type=code"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://clerk.com/sign-in"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://my-app.clerk.accounts.dev/sign-in"))
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl("https://company.okta.com/oauth2/v1/authorize"))
    }

    @Test
    fun testOAuthAuthorizationRequestWithoutRedirectUri() {
        // RFC 6749 Section 4.1.1: client_id is required; response_type indicates authorization request
        val authUrl = "https://service.com/authorize?client_id=123&response_type=code&utm_source=twitter"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(authUrl))
        assertEquals(authUrl, UrlSanitizer.stripTrackingParams(authUrl))
    }

    @Test
    fun testOAuthAuthorizationCallbackSuccessAndError() {
        // Success with code and state
        val successCallback = "https://myapp.com/api/auth/callback/google?code=40ax&state=xyz&utm_medium=cpc"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(successCallback))
        assertEquals(successCallback, UrlSanitizer.stripTrackingParams(successCallback))

        // Error with error and state
        val errorCallback = "https://myapp.com/api/auth/callback?error=access_denied&state=xyz&utm_source=ad"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(errorCallback))
        assertEquals(errorCallback, UrlSanitizer.stripTrackingParams(errorCallback))

        // Spring Security callback pattern
        val springCallback = "https://enterprise.com/login/oauth2/code/google?code=40ax&utm_medium=cpc"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(springCallback))
        assertEquals(springCallback, UrlSanitizer.stripTrackingParams(springCallback))
    }

    @Test
    fun testAuthorityPortHandling() {
        val portUrl = "https://github.com:8443/login/oauth/authorize?client_id=123&response_type=code"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(portUrl))
        val localDevUrl = "http://localhost:3000/api/auth/callback/google?code=abc&state=xyz"
        assertTrue(UrlSanitizer.isAuthOrOAuthUrl(localDevUrl))
    }

    @Test
    fun testPrivacyNegativeTests() {
        // Multi-purpose domain (GitHub) without OAuth parameters MUST have tracking stripped
        val githubRepo = "https://github.com/torvalds/linux?utm_source=twitter"
        assertFalse(UrlSanitizer.isAuthOrOAuthUrl(githubRepo))
        assertEquals("https://github.com/torvalds/linux", UrlSanitizer.stripTrackingParams(githubRepo))

        // Generic login page without OAuth parameters MUST have tracking stripped
        val loginPage = "https://example.com/login?utm_source=fb&fbclid=123"
        assertFalse(UrlSanitizer.isAuthOrOAuthUrl(loginPage))
        assertEquals("https://example.com/login", UrlSanitizer.stripTrackingParams(loginPage))

        // YouTube 'si' parameter MUST be stripped
        val youtubeLink = "https://youtu.be/video123?si=abcdef"
        assertEquals("https://youtu.be/video123", UrlSanitizer.stripTrackingParams(youtubeLink))

        // Google Ads click IDs MUST be stripped
        val gAdsLink = "https://example.com/product?gbraid=abc&wbraid=def"
        assertEquals("https://example.com/product", UrlSanitizer.stripTrackingParams(gAdsLink))
    }
}

