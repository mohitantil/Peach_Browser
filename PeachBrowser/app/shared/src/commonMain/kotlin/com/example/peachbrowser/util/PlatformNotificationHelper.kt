package com.example.peachbrowser.util

import com.example.peachbrowser.presentation.mvi.DownloadItem

expect fun postSensorNotification(origin: String, sensors: Set<SensorType>)
expect fun cancelSensorNotification()

expect fun openDownloadsFolder()
expect fun openDownloadedFile(item: DownloadItem)
expect fun deleteDownloadedFileFromStorage(item: DownloadItem)
expect fun syncDownloadsWithStorage(items: List<DownloadItem>): List<DownloadItem>
