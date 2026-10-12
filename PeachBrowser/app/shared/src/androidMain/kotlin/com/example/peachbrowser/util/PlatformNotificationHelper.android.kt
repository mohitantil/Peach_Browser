package com.example.peachbrowser.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

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
