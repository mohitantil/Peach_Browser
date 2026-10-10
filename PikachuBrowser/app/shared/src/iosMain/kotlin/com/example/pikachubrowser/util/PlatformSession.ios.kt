package com.example.pikachubrowser.util

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
