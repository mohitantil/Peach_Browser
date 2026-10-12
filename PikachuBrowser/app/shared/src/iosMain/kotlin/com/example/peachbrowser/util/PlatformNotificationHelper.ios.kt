package com.example.peachbrowser.util

actual fun postSensorNotification(origin: String, sensors: Set<SensorType>) {
    // iOS manages status bar camera/mic indicators natively at the system level.
}

actual fun cancelSensorNotification() {
    // iOS native sensor indicator cleanup
}

actual fun openDownloadsFolder() {
    // iOS Files app integration
}
