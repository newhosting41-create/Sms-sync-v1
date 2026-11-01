package com.gatenoc.bkashsync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SyncForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "bkash_sync_channel"
        const val NOTIF_ID = 7601
        const val ACTION_SYNC_NOW = "com.gatenoc.bkashsync.ACTION_SYNC_NOW"
        const val ACTION_UI_UPDATE = "com.gatenoc.bkashsync.ACTION_UI_UPDATE"

        fun startService(context: Context, syncNow: Boolean = false) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                if (syncNow) action = ACTION_SYNC_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        startForeground(NOTIF_ID, buildNotification("Service running. Monitoring incoming bKash SMS..."))

        serviceScope.launch {
            while (isActive) {
                processPendingQueueAndStatus()
                delay(30_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_NOW) {
            serviceScope.launch {
                processPendingQueueAndStatus()
            }
        }
        return START_STICKY
    }

    private suspend fun processPendingQueueAndStatus() {
        val endpoint = AppStorage.getEndpoint(this)
        val token = AppStorage.getToken(this)
        if (endpoint.isBlank() || token.length < 16) return

        val records = AppStorage.getRecords(this)

        val pendingList = records.filter { it.syncStatus == "PENDING" || it.syncStatus == "RETRY" }
        for (item in pendingList) {
            val res = ApiClient.syncSmsEvent(endpoint, token, item)
            if (res.success) {
                val newStatus = when (res.status.lowercase()) {
                    "flagged" -> "FLAGGED"
                    "duplicate" -> "DUPLICATE"
                    else -> "SYNCED"
                }
                AppStorage.updateStatus(this, item.trxId, newStatus, "Synced to server")
                updateNotification("Last synced TrxID: ${item.trxId} (Tk ${item.amountBdt})")
            } else if (res.httpCode in 400..499) {
                AppStorage.updateStatus(this, item.trxId, "FAILED", res.message)
            } else {
                AppStorage.updateStatus(this, item.trxId, "RETRY", res.message)
            }
        }

        val syncedIds = AppStorage.getRecords(this)
            .filter { it.syncStatus == "SYNCED" || it.syncStatus == "DUPLICATE" }
            .map { it.trxId }

        if (syncedIds.isNotEmpty()) {
            val statusMap = ApiClient.checkTrxStatuses(endpoint, token, syncedIds)
            for ((trx, st) in statusMap) {
                if (st == "used") {
                    AppStorage.updateStatus(this, trx, "USED", "Claimed by user on website")
                } else if (st == "flagged") {
                    AppStorage.updateStatus(this, trx, "FLAGGED", "Flagged for review")
                }
            }
        }

        sendBroadcast(Intent(ACTION_UI_UPDATE).setPackage(packageName))
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bKashSync::WakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "bKash SMS Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps bKash payment SMS listener active in background"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("bKash SMS Sync Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        super.onDestroy()

        if (AppStorage.isServiceEnabled(this)) {
            startService(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
