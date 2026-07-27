package com.rootapp.shield

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The friendly full-screen "pause" drawn over the offending app via SYSTEM_ALERT_WINDOW.
 * Programmatic Views (not Compose) for reliability when added from a background service.
 */
class InterruptOverlay(context: Context) {
    private val appCtx = context.applicationContext
    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(appLabel: String, onPause: () -> Unit, onProceed: () -> Unit) {
        if (view != null) return

        val root = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20A1E3C")) // near-opaque night blue
            setPadding(80, 80, 80, 80)
        }
        val orb = TextView(appCtx).apply {
            text = "●"
            setTextColor(Color.parseColor("#DCE9FA")); textSize = 56f; gravity = Gravity.CENTER
        }
        val title = TextView(appCtx).apply {
            text = "Hey — you opened $appLabel."
            setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
            setPadding(0, 40, 0, 16)
        }
        val msg = TextView(appCtx).apply {
            text = "Want to sit with me for 60 seconds instead?"
            setTextColor(Color.parseColor("#C4D3EA")); textSize = 15f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, 56)
        }
        val pause = Button(appCtx).apply {
            text = "Okay, let's pause"
            setOnClickListener { dismiss(); onPause() }
        }
        val proceed = Button(appCtx).apply {
            text = "Open anyway"
            setOnClickListener { dismiss(); onProceed() }
        }
        root.addView(orb); root.addView(title); root.addView(msg); root.addView(pause); root.addView(proceed)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            0, // focusable + touch-modal: captures interaction so the app behind is blocked
            PixelFormat.TRANSLUCENT,
        )
        view = root
        wm.addView(root, params)
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
