package com.zenlock.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.zenlock.data.BlockReason
import com.zenlock.data.Decision
import com.zenlock.data.ZenPasses
import com.zenlock.data.ZenState
import com.zenlock.data.ZenStore
import com.zenlock.ui.theme.ThemeMode
import com.zenlock.ui.theme.ZenPalette
import com.zenlock.ui.theme.ZenlockTheme
import com.zenlock.util.AppCatalog

/**
 * The screen the user actually meets. Two modes:
 *
 *  - **Block** — the limit is spent or Sleep mode is on. One way out: close.
 *  - **Pause** — Zen mode. A slow breath, the day's numbers, and only then a way through.
 *
 * The pause is the product. It is not there to be impossible, it is there to make the reach
 * conscious, which is what actually changes the count.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (pkg.isEmpty()) {
            finish()
            return
        }

        val isPause = intent.getStringExtra(EXTRA_MODE) == MODE_PAUSE
        val pauseSeconds = intent.getIntExtra(EXTRA_PAUSE_SECONDS, 10)
        val passMinutes = intent.getIntExtra(EXTRA_PASS_MINUTES, 5)
        val usedMs = intent.getLongExtra(EXTRA_USED_MS, 0L)
        val opens = intent.getIntExtra(EXTRA_OPENS, 0)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        val untilMinute = intent.getIntExtra(EXTRA_UNTIL_MINUTE, -1)
        val reason = intent.getStringExtra(EXTRA_REASON)
            ?.let { runCatching { BlockReason.valueOf(it) }.getOrNull() }

        val label = AppCatalog.labelFor(this, pkg)
        val icon = AppCatalog.iconFor(this, pkg)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val store = ZenStore.get(this)

        setContent {
            val stored by store.state.collectAsState(initial = ZenState())
            ZenlockTheme(
                palette = ZenPalette.from(stored.palette),
                themeMode = ThemeMode.from(stored.themeMode),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (isPause) {
                        PauseScreen(
                            label = label,
                            icon = icon,
                            seconds = pauseSeconds,
                            passMinutes = passMinutes,
                            usedMs = usedMs,
                            opens = opens,
                            limitMinutes = limitMinutes,
                            onOpen = {
                                ZenPasses.grant(pkg, passMinutes)
                                finish()
                            },
                            onDismiss = { goHome() },
                        )
                    } else {
                        BlockScreen(
                            label = label,
                            icon = icon,
                            reason = reason,
                            untilMinute = untilMinute,
                            usedMs = usedMs,
                            opens = opens,
                            limitMinutes = limitMinutes,
                            onDismiss = { goHome() },
                        )
                    }
                }
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_UNTIL_MINUTE = "until_minute"
        private const val EXTRA_PAUSE_SECONDS = "pause_seconds"
        private const val EXTRA_PASS_MINUTES = "pass_minutes"
        private const val EXTRA_USED_MS = "used_ms"
        private const val EXTRA_OPENS = "opens"
        private const val EXTRA_LIMIT_MINUTES = "limit_minutes"

        private const val MODE_BLOCK = "block"
        private const val MODE_PAUSE = "pause"

        fun intentFor(
            context: Context,
            packageName: String,
            decision: Decision,
            usedTodayMs: Long,
            opensToday: Int,
            limitMinutes: Int,
            passMinutes: Int,
        ): Intent = Intent(context, BlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            putExtra(EXTRA_PACKAGE, packageName)
            putExtra(EXTRA_USED_MS, usedTodayMs)
            putExtra(EXTRA_OPENS, opensToday)
            putExtra(EXTRA_LIMIT_MINUTES, limitMinutes)
            putExtra(EXTRA_PASS_MINUTES, passMinutes)
            when (decision) {
                is Decision.Pause -> {
                    putExtra(EXTRA_MODE, MODE_PAUSE)
                    putExtra(EXTRA_PAUSE_SECONDS, decision.seconds)
                }

                is Decision.Block -> {
                    putExtra(EXTRA_MODE, MODE_BLOCK)
                    putExtra(EXTRA_REASON, decision.reason.name)
                    putExtra(EXTRA_UNTIL_MINUTE, decision.untilMinute ?: -1)
                }

                Decision.Allow -> Unit
            }
        }
    }
}
