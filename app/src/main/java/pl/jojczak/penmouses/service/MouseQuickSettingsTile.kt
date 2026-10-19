package pl.jojczak.penmouses.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.Event
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus

/**
 * A user-added Samsung/Android Quick Settings tile that toggles mouse mode.
 * Does not attempt to grant accessibility privileges or start a disabled service.
 */
class MouseQuickSettingsTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observeJob?.cancel()
        observeJob = scope.launch {
            AppToServiceEvent.serviceStatus.collect { refreshTile(it) }
        }
        refreshTile(AppToServiceEvent.serviceStatus.value)
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        DiagnosticRecorder.record("quick-tile", "click connected=${MouseService.connected} current=${AppToServiceEvent.serviceStatus.value}")
        if (!MouseService.connected) {
            // Android requires the user to grant/enable accessibility themselves.
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        val mode = AppToServiceEvent.serviceStatus.value
        if (mode == ModeStatus.Mouse || mode == ModeStatus.Loading) {
            val accepted = AppToServiceEvent.event.tryEmit(Event.Stop)
            DiagnosticRecorder.record("quick-tile", "stop sent=$accepted")
            return
        }
        // The Samsung S Pen Remote SDK requires a resumed Activity for connect().
        // Never call Event.Start directly from the TileService when the UI is closed.
        val intent = Intent(this, MouseTileBridgeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
        DiagnosticRecorder.record("quick-tile", "requested bridge activity")
        // The tile follows the service's confirmed state; do not claim success on click.
    }

    private fun refreshTile(mode: ModeStatus) {
        val tile = qsTile ?: return
        // Keep the tile tappable while accessibility is off, so it can open settings.
        tile.state = if (MouseService.connected && mode == ModeStatus.Mouse && AppToServiceEvent.sPenReady.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !MouseService.connected -> "Dostępność wyłączona"
                mode == ModeStatus.Loading -> "Łączenie…"
                mode == ModeStatus.Mouse && AppToServiceEvent.sPenReady.value -> "Włączony"
                mode == ModeStatus.Mouse -> "Łączenie…"
                else -> "Wyłączony"
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
