package com.example.peachbrowser.util

expect fun postSensorNotification(origin: String, sensors: Set<SensorType>)
expect fun cancelSensorNotification()

expect fun openDownloadsFolder()
