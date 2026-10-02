package com.zenlock.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/**
 * Loads the launchable apps on the device. Uses the manifest `<queries>` declaration rather than
 * QUERY_ALL_PACKAGES, so Play review has nothing extra to argue about.
 */
object AppCatalog {

    private const val ICON_PX = 96

    @Volatile
    private var cache: List<AppEntry>? = null

    private fun launchablePackages(context: Context): List<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .toList()
    }

    /**
     * Names only, no icon decoding. Roughly an order of magnitude faster than [load], so the
     * daily total and the app list can be on screen before the icons arrive.
     */
    suspend fun loadLabels(context: Context): List<AppEntry> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            launchablePackages(context)
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        AppEntry(pkg, pm.getApplicationLabel(info).toString(), null)
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    suspend fun load(context: Context, refresh: Boolean = false): List<AppEntry> {
        cache?.takeIf { !refresh }?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            launchablePackages(context)
                .mapNotNull { pkg ->
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        AppEntry(
                            packageName = pkg,
                            label = pm.getApplicationLabel(info).toString(),
                            icon = pm.getApplicationIcon(info)
                                .toBitmap(ICON_PX, ICON_PX)
                                .asImageBitmap(),
                        )
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
                .also { cache = it }
        }
    }

    /** Best-effort label for a package we may not have catalogued (used by the block screen). */
    fun labelFor(context: Context, pkg: String): String = runCatching {
        val pm: PackageManager = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun iconFor(context: Context, pkg: String): ImageBitmap? = runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    }.getOrNull()
}
