package com.example.peachbrowser.util

import com.example.peachbrowser.presentation.mvi.DownloadItem

actual fun postSensorNotification(origin: String, sensors: Set<SensorType>) {}
actual fun cancelSensorNotification() {}
actual fun openDownloadsFolder() {}
actual fun openDownloadedFile(item: DownloadItem) {}
actual fun deleteDownloadedFileFromStorage(item: DownloadItem) {}
actual fun syncDownloadsWithStorage(items: List<DownloadItem>): List<DownloadItem> = items
