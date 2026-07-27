package com.rootapp.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rootapp.MainActivity
import com.rootapp.analytics.Events
import com.rootapp.analytics.Track
import com.rootapp.data.LocalStore
import com.rootapp.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls the foreground app (~800ms) and, when a monitored app
 * appears, draws the friendly interrupt overlay. Uses UsageStatsManager (D25) - no
 * AccessibilityService. Detection is sub-second, which is fine for a "pause" nudge.
 */
class UsageWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var usm: UsageStatsManager
    private lateinit var monitored: MonitoredApps
    private lateinit var overlay: InterruptOverlay
    private var lastForeground: String? = null

    override fun onCreate() {
        super.onCreate()
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        monitored = MonitoredApps(this)
        overlay = InterruptOverlay(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        startAsForeground()
        _running.value = true
        scope.launch { pollLoop() }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val current = currentForegroundPackage(now)
            // Transition-based: fire every time the user switches INTO a monitored app,
            // so re-opening YouTube later always re-triggers (fixes the "only first time" bug).
            if (current != null && current != lastForeground) {
                lastForeground = current
                val monitoredSet = monitored.get()
                if (current in monitoredSet && !overlay.isShowing) {
                    val label = current.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    LocalStore(this).incInterruptShown()
                    Track.event(Events.INTERRUPT_SHOWN)
                    val strict = SettingsStore(this).premium
                    main.post {
                        overlay.showForApp(
                            appLabel = label,
                            strict = strict,
                            onPause = {
                                LocalStore(this).incInterruptPaused()
                                Track.event(Events.INTERRUPT_PAUSED)
                                bringRootToFront()
                            },
                            onProceed = { Track.event(Events.INTERRUPT_OPENED_ANYWAY) },
                        )
                    }
                }
            }
            delay(POLL_MS)
        }
    }

    private fun currentForegroundPackage(now: Long): String? {
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val list = mutableListOf<UsageEventLite>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                list += UsageEventLite(e.packageName, e.timeStamp, toForeground = true)
            }
        }
        return ForegroundAppDetector.latestForegroundPackage(list)
    }

    private fun bringRootToFront() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(i)
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Root protection", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Root has your back")
            .setContentText("Watching for late-night scroll traps.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        _running.value = false
        overlay.dismiss()
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _running.value = false
        overlay.dismiss()
        if (scope.isActive) scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "root_protection"
        private const val NOTIF_ID = 42
        private const val POLL_MS = 800L
        private const val LOOKBACK_MS = 6_000L
        const val ACTION_STOP = "com.rootapp.shield.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, UsageWatcherService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, UsageWatcherService::class.java).setAction(ACTION_STOP))
        }
    }
}
