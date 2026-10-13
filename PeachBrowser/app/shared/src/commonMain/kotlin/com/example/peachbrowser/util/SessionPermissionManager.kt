package com.example.peachbrowser.util

enum class SensorType {
    MICROPHONE,
    CAMERA
}

/**
 * Strict Ephemeral In-Memory Permission Manager.
 * Zero database persistence, zero disk caching.
 * Permissions are tied to active tab sessions and cleared instantly when:
 * 1. The tab navigates to a new origin
 * 2. The tab is closed
 * 3. The browser application is destroyed
 */
object SessionPermissionManager {
    // Volatile RAM map: tabId -> (origin -> set of granted WebKit resource strings)
    private val activeTabResources = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

    // Volatile RAM map: tabId -> (origin -> set of granted sensors)
    private val activeTabPermissions = mutableMapOf<String, MutableMap<String, MutableSet<SensorType>>>()

    var onSensorActiveChanged: ((tabId: String, origin: String, activeSensors: Set<SensorType>) -> Unit)? = null

    init {
        onSensorActiveChanged = { _, origin, sensors ->
            if (sensors.isNotEmpty()) {
                postSensorNotification(origin, sensors)
            } else {
                cancelSensorNotification()
            }
        }
    }

    fun grantPermission(tabId: String, origin: String, sensor: SensorType) {
        val origins = activeTabPermissions.getOrPut(tabId) { mutableMapOf() }
        val sensors = origins.getOrPut(origin) { mutableSetOf() }
        sensors.add(sensor)
        onSensorActiveChanged?.invoke(tabId, origin, sensors.toSet())
    }

    fun grantResources(tabId: String, origin: String, resources: Array<String>) {
        val origins = activeTabResources.getOrPut(tabId) { mutableMapOf() }
        val granted = origins.getOrPut(origin) { mutableSetOf() }
        for (res in resources) {
            granted.add(res)
            if (res.contains("AUDIO_CAPTURE", ignoreCase = true)) {
                grantPermission(tabId, origin, SensorType.MICROPHONE)
            }
            if (res.contains("VIDEO_CAPTURE", ignoreCase = true)) {
                grantPermission(tabId, origin, SensorType.CAMERA)
            }
        }
    }

    fun hasPermission(tabId: String, origin: String, sensor: SensorType): Boolean {
        return activeTabPermissions[tabId]?.get(origin)?.contains(sensor) == true
    }

    fun hasAllResources(tabId: String, origin: String, resources: Array<String>): Boolean {
        val granted = activeTabResources[tabId]?.get(origin) ?: return false
        if (resources.isEmpty()) return false
        return resources.all { granted.contains(it) }
    }

    fun getGrantedResources(tabId: String, origin: String): Array<String> {
        return activeTabResources[tabId]?.get(origin)?.toTypedArray() ?: emptyArray()
    }

    fun hasOrigin(tabId: String, origin: String): Boolean {
        return activeTabResources[tabId]?.containsKey(origin) == true
    }

    fun revokeSitePermissions(tabId: String, origin: String) {
        activeTabResources[tabId]?.remove(origin)
        activeTabPermissions[tabId]?.remove(origin)
        onSensorActiveChanged?.invoke(tabId, origin, emptySet())
    }

    fun clearTabPermissions(tabId: String) {
        activeTabResources.remove(tabId)
        val origins = activeTabPermissions.remove(tabId)
        origins?.keys?.forEach { origin ->
            onSensorActiveChanged?.invoke(tabId, origin, emptySet())
        }
    }

    fun clearAllPermissions() {
        activeTabResources.clear()
        val allTabs = activeTabPermissions.keys.toList()
        for (tabId in allTabs) {
            clearTabPermissions(tabId)
        }
        activeTabPermissions.clear()
    }
}
