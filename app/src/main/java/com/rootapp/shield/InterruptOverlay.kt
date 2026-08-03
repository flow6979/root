package com.rootapp.shield

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The friendly full-screen "pause" over a time-sink app (SYSTEM_ALERT_WINDOW).
 *
 * Two gentle steps instead of a hard wall:
 *   1. "What brings you here?" - a purposeful reason (message / post) lets you through; an aimless
 *      one (bored / habit) leads to...
 *   2. a ~12s guided breath, after which you choose "I'm good, close" or "Open anyway".
 *
 * Programmatic Views (not Compose) for reliability when added from a background service.
 */
class InterruptOverlay(context: Context) {
    private val appCtx = context.applicationContext
    private val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var breathAnim: ValueAnimator? = null
    private var timer: CountDownTimer? = null

    val isShowing: Boolean get() = view != null

    fun showForApp(appLabel: String, strict: Boolean = false, onPause: () -> Unit, onProceed: () -> Unit) {
        if (view != null) return
        val root = rootLayout()
        renderIntent(root, appLabel, strict, onPause, onProceed)
        attach(root)
    }

    // Kept for the "test/preview" callers and geofence use.
    fun show(titleText: String, subtitleText: String, onPause: () -> Unit, onProceed: () -> Unit, allowProceed: Boolean = true) {
        if (view != null) return
        val root = rootLayout()
        renderBreath(root, titleText, allowProceed, onPause, onProceed)
        attach(root)
    }

    // ---- step 1: what brings you here? ----
    private fun renderIntent(root: LinearLayout, app: String, strict: Boolean, onPause: () -> Unit, onProceed: () -> Unit) {
        root.removeAllViews()
        root.addView(orb())
        root.addView(heading("You opened $app"))
        root.addView(body("What brings you here?"))
        val proceed = { dismiss(); onProceed() }
        val breathe = { renderBreath(root, "Let's take one breath together.", !strict, onPause, onProceed) }
        // Purposeful reasons pass through; aimless ones lead to the breath.
        root.addView(chip("I need to message someone") { if (strict) breathe() else proceed() })
        root.addView(chip("I want to post something") { if (strict) breathe() else proceed() })
        root.addView(chip("Just relaxing / a bit bored") { breathe() })
        root.addView(chip("Habit, no real reason") { breathe() })
    }

    // ---- step 2: one breath ----
    private fun renderBreath(root: LinearLayout, subtitle: String, allowProceed: Boolean, onPause: () -> Unit, onProceed: () -> Unit) {
        root.removeAllViews()
        val orb = orb()
        root.addView(orb)
        val phase = heading("Breathe in")
        root.addView(phase)
        root.addView(body(subtitle))

        // Orb scales with the breath (4s in / 4s out).
        breathAnim = ValueAnimator.ofFloat(0.75f, 1.35f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { orb.scaleX = it.animatedValue as Float; orb.scaleY = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationRepeat(a: android.animation.Animator) {
                    phase.text = if (phase.text == "Breathe in") "Breathe out" else "Breathe in"
                }
            })
            start()
        }

        val done = body("").apply { setPadding(0, 8, 0, 40) }
        root.addView(done)
        val pauseBtn = Button(appCtx).apply {
            text = "I'm good - close"; visibility = View.GONE
            setOnClickListener { dismiss(); onPause() }
        }
        val proceedBtn = Button(appCtx).apply {
            text = "Open anyway"; visibility = View.GONE
            setOnClickListener { dismiss(); onProceed() }
        }
        root.addView(pauseBtn)
        if (allowProceed) root.addView(proceedBtn)

        timer = object : CountDownTimer(12_000, 1000) {
            override fun onTick(ms: Long) { done.text = "${ms / 1000 + 1}" }
            override fun onFinish() {
                done.text = "How do you feel now?"
                pauseBtn.visibility = View.VISIBLE
                if (allowProceed) proceedBtn.visibility = View.VISIBLE
            }
        }.start()
    }

    // ---- shared view builders ----
    private fun rootLayout() = LinearLayout(appCtx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.parseColor("#F20A1E3C"))
        setPadding(80, 80, 80, 80)
    }

    private fun orb() = TextView(appCtx).apply {
        text = "●"; setTextColor(Color.parseColor("#DCE9FA")); textSize = 56f; gravity = Gravity.CENTER
    }

    private fun heading(t: String) = TextView(appCtx).apply {
        text = t; setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER; setPadding(0, 40, 0, 12)
    }

    private fun body(t: String) = TextView(appCtx).apply {
        text = t; setTextColor(Color.parseColor("#C4D3EA")); textSize = 15f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 28)
    }

    private fun chip(label: String, onClick: () -> Unit) = Button(appCtx).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun attach(root: LinearLayout) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, 0, PixelFormat.TRANSLUCENT,
        )
        view = root
        wm.addView(root, params)
    }

    fun dismiss() {
        breathAnim?.cancel(); breathAnim = null
        timer?.cancel(); timer = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
