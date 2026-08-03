package com.rootapp.shield

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.time.LocalDate

/**
 * Today's step count from the on-device TYPE_STEP_COUNTER sensor (cumulative-since-boot). We store
 * a per-day baseline so "today" = current - baseline. No Health Connect, no network. Degrades
 * gracefully: [available]/[hasPermission] are false when the phone has no counter or hasn't granted
 * activity recognition, and [sampleOnce] then returns null.
 */
object StepCounter {
    private fun sm(ctx: Context) = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("steps", Context.MODE_PRIVATE)

    fun available(ctx: Context): Boolean = sm(ctx).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Register briefly, read the cumulative counter, convert to today's steps, then unregister. */
    fun sampleOnce(ctx: Context, timeoutMs: Long = 4000, cb: (Int?) -> Unit) {
        val sensor = sm(ctx).getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null || !hasPermission(ctx)) { cb(null); return }
        val manager = sm(ctx)
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (done) return
                done = true
                val today = todayFrom(ctx, e.values.firstOrNull()?.toLong() ?: 0L)
                runCatching { manager.unregisterListener(this) }
                cb(today)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        runCatching { manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
            .onFailure { cb(null); return }
        handler.postDelayed({
            if (!done) { done = true; runCatching { manager.unregisterListener(listener) }; cb(null) }
        }, timeoutMs)
    }

    private fun todayFrom(ctx: Context, cumulative: Long): Int {
        val p = prefs(ctx)
        val today = LocalDate.now().toString()
        if (p.getString("day", null) != today) {
            // First reading of the day: this cumulative value becomes the baseline.
            p.edit().putString("day", today).putLong("baseline", cumulative).apply()
            return 0
        }
        val baseline = p.getLong("baseline", cumulative)
        return (cumulative - baseline).toInt().coerceAtLeast(0)
    }
}
