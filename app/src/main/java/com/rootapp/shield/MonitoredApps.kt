package com.rootapp.shield

import android.content.Context

/** SharedPreferences-backed set of package names Root will interrupt. */
class MonitoredApps(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("shield", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getStringSet(KEY, DEFAULT)?.toSet() ?: DEFAULT

    fun isMonitored(pkg: String): Boolean = pkg in get()

    fun set(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY, pkgs).apply()
    }

    fun toggle(pkg: String, on: Boolean) {
        val next = get().toMutableSet().apply { if (on) add(pkg) else remove(pkg) }
        set(next)
    }

    companion object {
        private const val KEY = "monitored_packages"
        val DEFAULT: Set<String> = setOf("com.instagram.android", "com.google.android.youtube")
    }
}
