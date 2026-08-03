package com.rootapp.shield

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Lists the user's launchable apps so any of them can be added to the "apps to pause" set, and
 * loads their real icons. Uses the launcher-intent query already declared in the manifest, so it
 * sees third-party apps on Android 11+ (package visibility).
 */
object InstalledApps {
    data class AppInfo(val pkg: String, val label: String)

    /** All launchable apps except Root itself, sorted by display name. */
    fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { AppInfo(it, UsageStatsReader.labelOf(context, it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** The app's launcher icon as an [ImageBitmap], or null if it can't be loaded. */
    fun icon(context: Context, pkg: String): ImageBitmap? {
        val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            val w = drawable.intrinsicWidth.coerceIn(1, 192)
            val h = drawable.intrinsicHeight.coerceIn(1, 192)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        return bitmap.asImageBitmap()
    }
}
