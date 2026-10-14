package com.example.peachbrowser.util

import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.WebKit.WKWebsiteDataStore

actual fun clearBrowserSession() {
    try {
        val dataStore = WKWebsiteDataStore.defaultDataStore()
        val dataTypes = WKWebsiteDataStore.allWebsiteDataTypes()
        val dateFrom = NSDate.dateWithTimeIntervalSince1970(0.0)
        dataStore.removeDataOfTypes(dataTypes, modifiedSince = dateFrom) {}
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun stopPlatformMediaService() {
}

actual fun exitBrowserApp() {
    try {
        clearBrowserSession()
        platform.posix.exit(0)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun shredSiteData(host: String) {
    try {
        val dataStore = WKWebsiteDataStore.defaultDataStore()
        val dataTypes = WKWebsiteDataStore.allWebsiteDataTypes()
        dataStore.fetchDataRecordsOfTypes(dataTypes) { records ->
            records?.forEach { record ->
                val r = record as? platform.WebKit.WKWebsiteDataRecord
                if (r != null && r.displayName.contains(host)) {
                    dataStore.removeDataOfTypes(r.dataTypes, listOf(r)) {}
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

