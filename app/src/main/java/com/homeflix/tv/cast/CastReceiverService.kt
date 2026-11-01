package com.homeflix.tv.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.homeflix.tv.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

/**
 * Keeps the cast connection alive while other apps are on screen, so the TV
 * stays in the browser's cast menu. Low-priority ongoing notification.
 */
@AndroidEntryPoint
class CastReceiverService : Service() {

    @Inject lateinit var connection: CastConnection

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
            .onFailure { Log.w(TAG, "startForeground failed", it) }
        connection.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connection.stop()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ready to cast", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("HomeFlix is ready to cast")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "CastService"
        private const val CHANNEL = "cast_ready"
        private const val NOTIFICATION_ID = 4200

        /** Start the receiver if casting is enabled. Never throws. */
        fun start(context: Context) {
            val app = context.applicationContext
            val entry = EntryPointAccessors.fromApplication(app, CastEntryPoint::class.java)
            if (!entry.castSettings().enabled.value) return
            val ok = runCatching {
                ContextCompat.startForegroundService(app, Intent(app, CastReceiverService::class.java))
            }.isSuccess
            // Background-start restrictions (Android 12+): keep the socket while the process lives.
            if (!ok) entry.castConnection().start()
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, CastReceiverService::class.java))
            EntryPointAccessors.fromApplication(app, CastEntryPoint::class.java).castConnection().stop()
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CastEntryPoint {
    fun castSettings(): CastSettings
    fun castConnection(): CastConnection
}

/** Brings the cast receiver up after the TV powers on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            CastReceiverService.start(context)
        }
    }
}
