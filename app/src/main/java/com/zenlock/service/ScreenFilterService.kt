package com.zenlock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zenlock.MainActivity
import com.zenlock.R
import com.zenlock.data.FilterSchedule
import com.zenlock.data.ZenStore
import com.zenlock.util.Time
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Draws the warmth + dimming overlay.
 *
 * Dimming uses an overlay rather than writing SCREEN_BRIGHTNESS, because an overlay can go
 * darker than the panel's own minimum brightness -- which is the whole point at 1am -- and
 * needs no WRITE_SETTINGS permission.
 */
class ScreenFilterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var warmthView: View? = null
    private var dimView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        startInForeground()

        val store = ZenStore.get(this)
        scope.launch {
            // React immediately to slider changes, and re-check the schedule on every tick.
            combine(store.state, ticker()) { state, _ -> state.filter }
                .collectLatest { applyFilter(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        detachOverlay()
        super.onDestroy()
    }

    /** Emits on a fixed cadence; cancelled together with [scope]. */
    private fun ticker() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private fun applyFilter(filter: FilterSchedule) {
        val scheduled = filter.enabled &&
            Time.isWithinWindow(filter.startMinute, filter.endMinute, Time.nowMinuteOfDay())
        val active = filter.forcedOn || scheduled

        if (!active || !Settings.canDrawOverlays(this)) {
            detachOverlay()
            return
        }

        attachOverlay()
        warmthView?.setBackgroundColor(warmthColor(filter.warmth))
        dimView?.setBackgroundColor(dimColor(filter.dim))
    }

    private fun attachOverlay() {
        if (overlay != null) return
        val wm = windowManager ?: return

        val warmth = View(this)
        val dim = View(this)
        val container = FrameLayout(this).apply {
            addView(warmth, matchParent())
            addView(dim, matchParent())
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching { wm.addView(container, params) }
            .onSuccess {
                overlay = container
                warmthView = warmth
                dimView = dim
            }
    }

    private fun detachOverlay() {
        val wm = windowManager
        val current = overlay ?: return
        if (wm != null) runCatching { wm.removeView(current) }
        overlay = null
        warmthView = null
        dimView = null
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private fun startInForeground() {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.filter_notification_title))
            .setContentText(getString(R.string.filter_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(tap)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val CHANNEL_ID = "screen_filter"
        private const val NOTIFICATION_ID = 42
        private const val TICK_MS = 30_000L

        /** Amber, capped well short of opaque so the screen stays usable. */
        private fun warmthColor(warmth: Int): Int {
            val alpha = (warmth.coerceIn(0, 100) / 100f * 0.55f * 255).toInt()
            return Color.argb(alpha, 255, 138, 20)
        }

        private fun dimColor(dim: Int): Int {
            val alpha = (dim.coerceIn(0, 80) / 100f * 255).toInt()
            return Color.argb(alpha, 0, 0, 0)
        }

        /** Starts the service when the filter can ever fire, stops it when it cannot. */
        fun sync(context: Context, filter: FilterSchedule) {
            val intent = Intent(context, ScreenFilterService::class.java)
            if (filter.enabled || filter.forcedOn) {
                runCatching { context.startForegroundService(intent) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }

        suspend fun syncFromStore(context: Context) {
            sync(context, ZenStore.get(context).state.first().filter)
        }
    }
}
