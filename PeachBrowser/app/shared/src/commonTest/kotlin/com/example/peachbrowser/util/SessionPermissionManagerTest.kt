package com.example.peachbrowser.util

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPermissionManagerTest {

    @BeforeTest
    @AfterTest
    fun cleanup() {
        SessionPermissionManager.clearAllPermissions()
    }

    @Test
    fun testGrantResourcesAndSensorMapping() {
        val tabId = "tab_101"
        val origin = "https://meet.jit.si"

        val resources = arrayOf("android.webkit.resource.AUDIO_CAPTURE", "android.webkit.resource.VIDEO_CAPTURE")
        SessionPermissionManager.grantResources(tabId, origin, resources)

        assertTrue(SessionPermissionManager.hasPermission(tabId, origin, SensorType.MICROPHONE))
        assertTrue(SessionPermissionManager.hasPermission(tabId, origin, SensorType.CAMERA))
        assertTrue(SessionPermissionManager.hasAllResources(tabId, origin, resources))
        assertTrue(SessionPermissionManager.hasOrigin(tabId, origin))

        val granted = SessionPermissionManager.getGrantedResources(tabId, origin)
        assertEquals(2, granted.size)
        assertTrue(granted.contains("android.webkit.resource.AUDIO_CAPTURE"))
        assertTrue(granted.contains("android.webkit.resource.VIDEO_CAPTURE"))
    }

    @Test
    fun testOriginAndTabIsolation() {
        val tab1 = "tab_1"
        val tab2 = "tab_2"
        val originA = "https://alpha.com"
        val originB = "https://beta.com"

        SessionPermissionManager.grantPermission(tab1, originA, SensorType.CAMERA)

        // Same tab, different origin must be isolated
        assertFalse(SessionPermissionManager.hasPermission(tab1, originB, SensorType.CAMERA))

        // Different tab, same origin must be isolated
        assertFalse(SessionPermissionManager.hasPermission(tab2, originA, SensorType.CAMERA))
    }

    @Test
    fun testRevokeSitePermissions() {
        val tabId = "tab_1"
        val originA = "https://siteA.com"
        val originB = "https://siteB.com"

        SessionPermissionManager.grantPermission(tabId, originA, SensorType.MICROPHONE)
        SessionPermissionManager.grantPermission(tabId, originB, SensorType.CAMERA)

        SessionPermissionManager.revokeSitePermissions(tabId, originA)

        assertFalse(SessionPermissionManager.hasPermission(tabId, originA, SensorType.MICROPHONE))
        assertTrue(SessionPermissionManager.hasPermission(tabId, originB, SensorType.CAMERA))
    }

    @Test
    fun testClearTabPermissionsOnNavigation() {
        val tabId = "tab_active"
        val origin = "https://meeting.com"

        SessionPermissionManager.grantPermission(tabId, origin, SensorType.MICROPHONE)
        assertTrue(SessionPermissionManager.hasPermission(tabId, origin, SensorType.MICROPHONE))

        // Simulating tab navigation
        SessionPermissionManager.clearTabPermissions(tabId)

        assertFalse(SessionPermissionManager.hasPermission(tabId, origin, SensorType.MICROPHONE))
        assertFalse(SessionPermissionManager.hasOrigin(tabId, origin))
    }

    @Test
    fun testClearAllPermissionsOnSessionDestroy() {
        SessionPermissionManager.grantPermission("tab_1", "https://site1.com", SensorType.CAMERA)
        SessionPermissionManager.grantPermission("tab_2", "https://site2.com", SensorType.MICROPHONE)

        SessionPermissionManager.clearAllPermissions()

        assertFalse(SessionPermissionManager.hasPermission("tab_1", "https://site1.com", SensorType.CAMERA))
        assertFalse(SessionPermissionManager.hasPermission("tab_2", "https://site2.com", SensorType.MICROPHONE))
    }
}

