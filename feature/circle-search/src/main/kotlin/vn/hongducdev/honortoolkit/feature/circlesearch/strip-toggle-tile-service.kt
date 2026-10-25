package vn.hongducdev.honortoolkit.feature.circlesearch

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class StripToggleTileService : TileService() {
    override fun onStartListening() = render()

    override fun onClick() {
        val config = TriggerConfig(this)
        if (!Settings.canDrawOverlays(this)) return
        config.stripEnabled = !config.stripEnabled
        NavBarStripService.sync(this)
        render()
    }

    private fun render() {
        qsTile?.apply {
            state = if (TriggerConfig(this@StripToggleTileService).stripEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
