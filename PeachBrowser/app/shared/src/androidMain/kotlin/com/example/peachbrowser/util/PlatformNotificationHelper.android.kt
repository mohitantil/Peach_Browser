package com.example.peachbrowser.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import com.example.peachbrowser.presentation.mvi.DownloadItem
import java.io.File

object AndroidContextHolder {
    var context: Context? = null
}

private const val SENSOR_NOTIFICATION_ID = 8080
private const val SENSOR_CHANNEL_ID = "peach_sensors_channel"

actual fun postSensorNotification(origin: String, sensors: Set<SensorType>) {
    val ctx = AndroidContextHolder.context ?: return
    try {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SENSOR_CHANNEL_ID,
                "Privacy Sensors",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status when camera or microphone is in use by a webpage"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val sensorLabels = sensors.map {
            when (it) {
                SensorType.MICROPHONE -> "Microphone"
                SensorType.CAMERA -> "Camera"
            }
        }.joinToString(" & ")

        val notification = NotificationCompat.Builder(ctx, SENSOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("🔴 Privacy Shield: $sensorLabels Active")
            .setContentText("Site: $origin • Session-only permission")
            .setSubText("Tap or close tab to revoke")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        manager.notify(SENSOR_NOTIFICATION_ID, notification)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun cancelSensorNotification() {
    val ctx = AndroidContextHolder.context ?: return
    try {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(SENSOR_NOTIFICATION_ID)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun openDownloadsFolder() {
    val ctx = AndroidContextHolder.context ?: return
    try {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun openDownloadedFile(item: DownloadItem) {
    val ctx = AndroidContextHolder.context ?: return
    try {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val contentUri = if (item.id > 0 && dm != null) {
            try {
                dm.getUriForDownloadedFile(item.id)
            } catch (_: Exception) {
                null
            }
        } else null

        val targetUri = contentUri ?: item.localUri?.takeIf { it.startsWith("content://") }?.let { Uri.parse(it) }
        if (targetUri != null) {
            val mime = item.mimeType.ifBlank { dm?.getMimeTypeForDownloadedFile(item.id) ?: "*/*" }
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, mime)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            ctx.startActivity(viewIntent)
            return
        }
    } catch (_: Exception) {}
    openDownloadsFolder()
}

actual fun deleteDownloadedFileFromStorage(item: DownloadItem) {
    val ctx = AndroidContextHolder.context ?: return
    try {
        if (item.id > 0) {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.remove(item.id)
        }
        item.filePath?.let { path ->
            val f = File(path)
            if (f.exists()) f.delete()
        }
        val pubFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            item.fileName
        )
        if (pubFile.exists()) {
            pubFile.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun doesFileExistOnPhone(ctx: Context, item: DownloadItem, resolvedLocalUri: String?): Boolean {
    try {
        // 1. Check explicit filePath in public Downloads directory
        if (!item.filePath.isNullOrBlank()) {
            val f = File(item.filePath)
            if (f.exists() && f.length() > 0L) return true
        }

        // 2. Check resolved localUri from DownloadManager
        val uriStr = resolvedLocalUri ?: item.localUri
        if (!uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "file") {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val f = File(path)
                    return f.exists()
                }
            } else if (uri.scheme == "content") {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    pfd.close()
                    return true
                }
            }
        }

        // 3. Check standard public Downloads folder by fileName
        if (item.fileName.isNotBlank()) {
            val pubFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                item.fileName
            )
            if (pubFile.exists()) return true
        }
    } catch (_: Exception) {}
    return false
}

actual fun syncDownloadsWithStorage(items: List<DownloadItem>): List<DownloadItem> {
    val ctx = AndroidContextHolder.context ?: return items
    if (items.isEmpty()) return items
    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return items

    val syncedList = mutableListOf<DownloadItem>()

    for (item in items) {
        // Direct data-URI or base64 saved files have negative/synthetic IDs
        if (item.id <= 0L) {
            if (doesFileExistOnPhone(ctx, item, item.localUri)) {
                syncedList.add(item.copy(progress = 100, isCompleted = true))
            }
            continue
        }

        var cursorFound = false
        var shouldKeep = true
        var updatedItem = item

        try {
            val query = DownloadManager.Query().setFilterById(item.id)
            dm.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursorFound = true
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

                    val status = if (statusIdx != -1) cursor.getInt(statusIdx) else DownloadManager.STATUS_RUNNING
                    val bytesSoFar = if (downloadedIdx != -1) cursor.getLong(downloadedIdx) else 0L
                    val totalBytes = if (totalIdx != -1) cursor.getLong(totalIdx) else -1L
                    val localUri = if (localUriIdx != -1) cursor.getString(localUriIdx) else item.localUri
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) else item.fileName

                    val resolvedFilePath = if (!localUri.isNullOrBlank() && localUri.startsWith("file://")) {
                        Uri.parse(localUri).path ?: item.filePath
                    } else {
                        item.filePath
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val candidate = item.copy(
                                fileName = if (!title.isNullOrBlank()) title else item.fileName,
                                progress = 100,
                                isCompleted = true,
                                isFailed = false,
                                localUri = localUri ?: item.localUri,
                                filePath = resolvedFilePath,
                                bytesDownloaded = if (totalBytes > 0L) totalBytes else bytesSoFar,
                                totalBytes = totalBytes
                            )
                            if (doesFileExistOnPhone(ctx, candidate, localUri)) {
                                updatedItem = candidate
                            } else {
                                // User deleted the file from phone storage
                                shouldKeep = false
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            updatedItem = item.copy(
                                isCompleted = false,
                                isFailed = true
                            )
                        }
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> {
                            val calcProgress = when {
                                totalBytes > 0L -> ((bytesSoFar * 100L) / totalBytes).toInt().coerceIn(1, 99)
                                bytesSoFar > 0L -> 50
                                else -> item.progress.coerceAtLeast(5)
                            }
                            updatedItem = item.copy(
                                fileName = if (!title.isNullOrBlank()) title else item.fileName,
                                progress = calcProgress,
                                isCompleted = false,
                                isFailed = false,
                                localUri = localUri ?: item.localUri,
                                filePath = resolvedFilePath,
                                bytesDownloaded = bytesSoFar,
                                totalBytes = totalBytes
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!cursorFound) {
            // Removed from DownloadManager — check if the file still exists in public Downloads
            if (doesFileExistOnPhone(ctx, item, item.localUri)) {
                updatedItem = item.copy(progress = 100, isCompleted = true)
            } else {
                shouldKeep = false
            }
        }

        if (shouldKeep) {
            syncedList.add(updatedItem)
        }
    }

    return syncedList
}
