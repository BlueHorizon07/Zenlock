package com.zenlock.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.zenlock.service.ZenAccessibilityService

/**
 * Zenlock needs three of Android's "special" permissions. None of them can be granted from
 * inside an app: usage access, accessibility and overlay are all deliberately gated behind
 * Settings, with no runtime-dialog API, precisely because they are powerful. The most an app
 * can do is deep-link as close to the switch as the platform allows, which is what the
 * open* functions below do.
 */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasAccessibility(context: Context): Boolean {
        val expected = ComponentName(context, ZenAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry.trim()) == expected
        }
    }

    fun allGranted(context: Context): Boolean =
        hasUsageAccess(context) && hasOverlay(context) && hasAccessibility(context)

    /**
     * Lands on our own row where the OEM supports it; several do honour the package URI even
     * though there is no documented per-app usage-access screen.
     */
    fun openUsageAccess(context: Context) = launchFirst(
        context,
        Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    /**
     * The full accessibility list, because there is no way to do better.
     *
     * ACCESSIBILITY_DETAILS_SETTINGS would land on Zenlock's own switch, but it is guarded by
     * OPEN_ACCESSIBILITY_DETAILS_SETTINGS — signature-level, so no ordinary app can hold it,
     * and attempting it throws SecurityException rather than failing quietly. The user has to
     * find "Zenlock" in the list.
     */
    fun openAccessibility(context: Context) {
        launchFirst(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** This one genuinely is a single toggle for our app, courtesy of the package URI. */
    fun openOverlay(context: Context) = launchFirst(
        context,
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    /**
     * Tries each intent in turn. OEM Settings apps vary in what they accept, and a guarded
     * Settings action throws SecurityException rather than simply not resolving — catching
     * only ActivityNotFoundException here crashed the app, so the net is deliberately wide.
     */
    private fun launchFirst(context: Context, vararg candidates: Intent?) {
        for (intent in candidates) {
            if (intent == null) continue
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                Log.w(TAG, "Could not open ${intent.action}", e)
            }
        }
    }

    private const val TAG = "Permissions"
}
