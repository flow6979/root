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
import com.rootapp.di.AppModule
import java.util.Calendar
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

    // Sustained-use tracking for the "gentle nudge" notification.
    private var nudgeApp: String? = null
    private var nudgeSessionStart = 0L
    private var lastNudgeAt = 0L
    private var nextNudgeMin = FIRST_NUDGE_MIN

    // Adaptive-nudge outcome tracking: did they leave the app soon after a nudge (heeded)?
    private var pendingHeedApp: String? = null
    private var pendingHeedDeadline = 0L

    // Daily screen-time budget nudges (checked at most once a minute).
    private var lastBudgetCheck = 0L
    private var budgetDay = -1
    private var nudged80 = false
    private var nudgedFull = false

    // Move nudges (stationary detection via the step counter).
    private var lastMoveCheck = 0L
    private var lastMoveSteps = -1
    private var lastMoveStamp = 0L

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
        SettingsStore(this).protectionEnabled = true // so BootReceiver restarts us after a reboot
        scope.launch { pollLoop() }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val current = currentForegroundPackage(now)
            val monitoredSet = monitored.get()
            // Transition-based: fire every time the user switches INTO a monitored app,
            // so re-opening YouTube later always re-triggers (fixes the "only first time" bug).
            if (current != null && current != lastForeground) {
                lastForeground = current
                if (current in monitoredSet && !overlay.isShowing) {
                    val label = UsageStatsReader.labelOf(this, current)
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
            handleSustainedUse(current, monitoredSet, now)
            resolveHeed(current, now)
            maybeBudgetNudge(now)
            maybeMoveNudge(now)

            delay(POLL_MS)
        }
    }

    /** Fire a gentle nudge when a monitored app is used for a long continuous stretch. */
    private fun handleSustainedUse(current: String?, monitoredSet: Set<String>, now: Long) {
        if (current == null || current !in monitoredSet) { nudgeApp = null; return }
        if (nudgeApp != current) {
            nudgeApp = current
            nudgeSessionStart = now
            // Just-in-time: nudge earlier in this person's historically risky hours, later in calm ones.
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            nextNudgeMin = AdaptiveNudge.firstNudgeMin(hour, NudgeStore(this).riskByHour(), base = FIRST_NUDGE_MIN)
            return
        }
        val sessionMin = ((now - nudgeSessionStart) / 60000L).toInt()
        if (sessionMin < nextNudgeMin) return
        if (now - lastNudgeAt < NUDGE_COOLDOWN_MS) return
        if (!SettingsStore(this).overuseNudges || !Nudges.canPost(this)) return
        lastNudgeAt = now
        val store = NudgeStore(this)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        store.recordNudge(hour) // learn: this hour is a risky one for them
        pendingHeedApp = current // watch whether they leave the app soon (nudge worked)
        pendingHeedDeadline = now + HEED_WINDOW_MS
        // Anti-nag: widen the gap if nudges are being ignored, tighten if they're working.
        nextNudgeMin = sessionMin + AdaptiveNudge.repeatNudgeMin(store.heeded(), store.shown(), base = REPEAT_NUDGE_MIN)
        fireNudge(current, sessionMin)
    }

    /** After a nudge, if they leave the app within the window it worked; otherwise it was ignored. */
    private fun resolveHeed(current: String?, now: Long) {
        val app = pendingHeedApp ?: return
        if (current != app) {
            NudgeStore(this).recordHeeded()
            com.rootapp.data.Leaderboard.record(this, scope, com.rootapp.data.EPAction.NUDGE_HEEDED)
            pendingHeedApp = null
        } else if (now >= pendingHeedDeadline) {
            pendingHeedApp = null
        }
    }

    private fun fireNudge(pkg: String, sessionMin: Int) {
        scope.launch {
            val label = labelFor(pkg)
            val todayMin = UsageStatsReader.todayForegroundMinutes(this@UsageWatcherService, pkg)
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val nudge = NudgeCalculator.compute(label, sessionMin, todayMin, hour)
            val ai = NudgeContent.aiLine(AppModule.llmClient, label, sessionMin, hour)
            Nudges.post(this@UsageWatcherService, nudge.title, nudge.body(ai))
            Track.event(Events.NUDGE_SHOWN)
        }
    }

    private fun labelFor(pkg: String): String = UsageStatsReader.labelOf(this, pkg)

    /** Once a minute, nudge at 80% and 100% of the daily screen-time budget (once each per day). */
    private fun maybeBudgetNudge(now: Long) {
        if (now - lastBudgetCheck < 60_000L) return
        lastBudgetCheck = now
        val budget = SettingsStore(this).screenBudgetMin
        if (budget <= 0 || !Nudges.canPost(this)) return
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (day != budgetDay) { budgetDay = day; nudged80 = false; nudgedFull = false }
        val total = UsageStatsReader.todayTotalMinutes(this)
        val fmt = UsageStatsReader::fmt
        when {
            total >= budget && !nudgedFull -> {
                nudgedFull = true
                Nudges.post(
                    this, "You've hit today's screen budget",
                    "You're at ${fmt(total)}, past your ${fmt(budget)} goal. A good moment to set the " +
                        "phone down and do one real thing you'll be glad about.",
                )
            }
            total >= budget * 8 / 10 && !nudged80 -> {
                nudged80 = true
                Nudges.post(
                    this, "Almost at your screen budget",
                    "You're at ${fmt(total)} of ${fmt(budget)} today - about ${fmt((budget - total).coerceAtLeast(0))} left. Spend it on purpose.",
                )
            }
        }
    }

    /** Every ~10 min in daytime, nudge to move if the step count has barely changed for 90 min. */
    private fun maybeMoveNudge(now: Long) {
        if (now - lastMoveCheck < 10 * 60_000L) return
        lastMoveCheck = now
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 21) return
        if (!SettingsStore(this).overuseNudges || !StepCounter.available(this) ||
            !StepCounter.hasPermission(this) || !Nudges.canPost(this)
        ) {
            return
        }
        StepCounter.sampleOnce(this) { s ->
            if (s == null) return@sampleOnce
            if (lastMoveSteps < 0 || s - lastMoveSteps >= 150) { lastMoveSteps = s; lastMoveStamp = now; return@sampleOnce }
            if (now - lastMoveStamp >= 90 * 60_000L && now - lastNudgeAt >= NUDGE_COOLDOWN_MS) {
                lastMoveStamp = now; lastNudgeAt = now
                Nudges.post(
                    this, "Time to move",
                    "You've been still for a while. A 2-minute walk or a stretch resets your focus and lifts " +
                        "your mood - your body will thank you.",
                )
            }
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
        SettingsStore(this).protectionEnabled = false
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
        private const val FIRST_NUDGE_MIN = 15
        private const val REPEAT_NUDGE_MIN = 20
        private const val NUDGE_COOLDOWN_MS = 10 * 60 * 1000L
        private const val HEED_WINDOW_MS = 3 * 60 * 1000L
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
