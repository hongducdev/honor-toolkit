package vn.hongducdev.honortoolkit.feature.circlesearch

import android.app.Activity
import android.os.Bundle

class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = TriggerConfig(this)
        val fromTile = intent.getBooleanExtra(EXTRA_FROM_TILE, false)
        val source = if (fromTile) TriggerSource.Tile else TriggerSource.Shortcut
        TriggerRunner.run(this, if (fromTile) config.tileDelayMs else config.delayMs, source)
        finish()
    }

    companion object {
        const val EXTRA_FROM_TILE = "from_tile"
    }
}
