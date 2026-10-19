package pl.jojczak.penmouses.service

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.Event
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent.ModeStatus
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder

/**
 * Samsung S Pen Remote SDK's connect() needs a live Activity. A Quick Settings Tile
 * is a Service and cannot supply an Activity directly. This minimal, translucent
 * Activity stays resumed until the SDK connects or a bounded timeout expires.
 */
class MouseTileBridgeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var started = false
    private val timeout = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        DiagnosticRecorder.record("quick-tile", "connection timeout; closing bridge")
        Toast.makeText(this, "Nie udało się połączyć z S Penem. Spróbuj ponownie.", Toast.LENGTH_LONG).show()
        AppToServiceEvent.event.tryEmit(Event.Stop)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticRecorder.record("quick-tile", "bridge activity created")
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        if (!MouseService.connected) {
            Toast.makeText(this, "Włącz usługę dostępności PenMouse S", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        observer = scope.launch {
            AppToServiceEvent.sPenReady.first { it }
            DiagnosticRecorder.record("quick-tile", "S Pen ready; close bridge")
            handler.removeCallbacks(timeout)
            // Allow the SDK to complete listener setup before activity finishes.
            handler.postDelayed({ if (!isFinishing) finish() }, 900L)
        }
        handler.postDelayed(timeout, 11_000L)
        handler.postDelayed({
            if (!isFinishing) {
                val sent = AppToServiceEvent.event.tryEmit(Event.Start(ModeStatus.Mouse))
                DiagnosticRecorder.record("quick-tile", "bridge requested mouse start sent=$sent")
                if (!sent) {
                    handler.removeCallbacks(timeout)
                    finish()
                }
            }
        }, 150L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
