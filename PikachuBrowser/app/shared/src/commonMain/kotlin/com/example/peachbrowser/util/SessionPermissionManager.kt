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

    fun hasPermission(tabId: String, origin: String, sensor: SensorType): Boolean {
        return activeTabPermissions[tabId]?.get(origin)?.contains(sensor) == true
    }

    fun revokeSitePermissions(tabId: String, origin: String) {
        activeTabPermissions[tabId]?.remove(origin)
        onSensorActiveChanged?.invoke(tabId, origin, emptySet())
    }

    fun clearTabPermissions(tabId: String) {
        val origins = activeTabPermissions.remove(tabId)
        origins?.keys?.forEach { origin ->
            onSensorActiveChanged?.invoke(tabId, origin, emptySet())
        }
    }

    fun clearAllPermissions() {
        val allTabs = activeTabPermissions.keys.toList()
        for (tabId in allTabs) {
            clearTabPermissions(tabId)
        }
    }
}
