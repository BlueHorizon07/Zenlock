package com.zenlock.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zenlock.data.ZenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for the master switch.
 *
 * Turning enforcement off should not require finding and opening the app — if it does, people
 * uninstall instead, and an uninstalled blocker blocks nothing.
 */
class ZenTileService : TileService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch { render(ZenStore.get(applicationContext).current().enabled) }
    }

    override fun onStopListening() {
        scope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val store = ZenStore.get(applicationContext)
        scope.launch {
            store.update { it.copy(enabled = !it.enabled) }
            render(store.current().enabled)
        }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (enabled) "On" else "Off"
        tile.updateTile()
    }
}
