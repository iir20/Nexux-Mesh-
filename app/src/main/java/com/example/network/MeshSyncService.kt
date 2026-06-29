package com.example.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class MeshSyncService : Service() {
    companion object {
        private const val CHANNEL_ID = "mesh_sync_channel"
        private const val NOTIFICATION_ID = 9112
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Mesh Core")
            .setContentText("Background Multi-Peer Synchronization Active")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d("MeshSyncService", "Nexus Mesh Sync Service started in foreground")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexus Mesh Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Nexus Mesh synchronized in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
