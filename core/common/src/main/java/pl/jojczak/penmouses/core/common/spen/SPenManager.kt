package pl.jojczak.penmouses.core.common.spen

import android.util.Log
import android.os.Handler
import android.os.Looper
import pl.jojczak.penmouses.core.common.utils.OptionalTelemetry
import com.samsung.android.sdk.penremote.AirMotionEvent
import com.samsung.android.sdk.penremote.ButtonEvent
import com.samsung.android.sdk.penremote.SpenEventListener
import com.samsung.android.sdk.penremote.SpenRemote
import com.samsung.android.sdk.penremote.SpenUnit
import com.samsung.android.sdk.penremote.SpenUnitManager
import pl.jojczak.penmouses.core.common.di.ActivityProvider
import pl.jojczak.penmouses.core.common.spen.listener.AirMotionEventListener
import pl.jojczak.penmouses.core.common.spen.listener.ButtonEventListener
import pl.jojczak.penmouses.core.common.spen.listener.ConnectionResultCallback
import javax.inject.Singleton

@Singleton
class SPenManager(
    private val activityProvider: ActivityProvider,
) {
    var isSPenButtonDown = false
    var onConnectionLost: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionGeneration = 0L

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private var sPenUnitManager: SpenUnitManager? = null
    private var isAirMotionRegistered = false

    fun connect(sPenManagerConnectionResultCallback: ConnectionResultCallback) {
        Log.i(TAG, "Connecting to S-Pen...")

        val activity = activityProvider.getActivity() ?: run {
            Log.e(TAG, "Activity is null, cannot connect to S-Pen")
            sPenManagerConnectionResultCallback.onFailure(SpenRemote.Error.UNKNOWN)
            return
        }

        if (isSPenSupported()) {
            val sPenRemote = SpenRemote.getInstance()
            val generation = ++connectionGeneration
            sPenRemote.setConnectionStateChangeListener { state ->
                onMain {
                    if (generation == connectionGeneration && state != SpenRemote.State.CONNECTED) {
                        isSPenButtonDown = false
                        onConnectionLost?.invoke()
                    }
                }
            }

            if (sPenRemote.isConnected && sPenUnitManager != null) {
                sPenManagerConnectionResultCallback.onSuccess()
            } else {
                sPenRemote.connect(
                    activity,
                    SPenRemoteConnectionResultCallback(sPenManagerConnectionResultCallback, generation)
                )
            }
        } else {
            Log.w(TAG, "S-Pen is not supported, cannot connect")
            sPenManagerConnectionResultCallback.onFailure(SpenRemote.Error.UNSUPPORTED_DEVICE)
        }
    }

    private inner class SPenRemoteConnectionResultCallback(
        val sPenManagerConnectionResultCallback: ConnectionResultCallback,
        val generation: Long,
    ) : SpenRemote.ConnectionResultCallback {
        override fun onSuccess(sPenUnitManager: SpenUnitManager?) {
            Log.i(TAG, "Connection successful")
            onMain {
                if (generation != connectionGeneration) return@onMain
                this@SPenManager.sPenUnitManager = sPenUnitManager
                sPenManagerConnectionResultCallback.onSuccess()
            }
        }

        override fun onFailure(errorCode: Int) {
            onMain {
                if (generation == connectionGeneration) sPenManagerConnectionResultCallback.onFailure(errorCode)
            }
        }
    }

    fun registerButtonEventListener(
        buttonEventListener: ButtonEventListener,
        errorCallback: (Int) -> Unit
    ) {
        sPenUnitManager?.let { unitManager ->
            val buttonUnit = unitManager.getUnit(SpenUnit.TYPE_BUTTON)
            val generation = connectionGeneration

            val mButtonEventListener = SpenEventListener { event ->
                val mEvent = ButtonEvent(event)

                onMain {
                    if (generation != connectionGeneration) return@onMain
                    val down = mEvent.action == ButtonEvent.ACTION_DOWN
                    if (down == isSPenButtonDown) return@onMain
                    isSPenButtonDown = down
                    buttonEventListener.onEvent(mEvent.action, mEvent.timeStamp)
                }
            }

            try {
                unitManager.registerSpenEventListener(mButtonEventListener, buttonUnit)
            } catch (e: SecurityException) {
                OptionalTelemetry.recordException(e)
                Log.e(TAG, "Error registering button event listener", e)
                errorCallback(SpenRemote.Error.UNSUPPORTED_DEVICE)
            } catch (e: Exception) {
                OptionalTelemetry.recordException(e)
                Log.e(TAG, "Error registering button event listener", e)
                errorCallback(SpenRemote.Error.UNKNOWN)
            }

            Log.i(TAG, "Button event listener registered")
        }
    }

    fun registerAirMotionEventListener(
        airMotionEventListener: AirMotionEventListener,
        errorCallback: (Int) -> Unit
    ) {
        if (isAirMotionRegistered) {
            Log.w(TAG, "Air motion event listener already registered")
            return
        }
        isAirMotionRegistered = true

        sPenUnitManager?.let { unitManager ->
            val airMotionUnit = unitManager.getUnit(SpenUnit.TYPE_AIR_MOTION)
            val generation = connectionGeneration

            val mAirMotionEventListener = SpenEventListener { event ->
                val mEvent = AirMotionEvent(event)
                onMain {
                    if (generation == connectionGeneration && isAirMotionRegistered) {
                        airMotionEventListener.onEvent(mEvent.deltaX, mEvent.deltaY, mEvent.timeStamp)
                    }
                }
            }

            try {
                unitManager.registerSpenEventListener(mAirMotionEventListener, airMotionUnit)
            } catch (e: SecurityException) {
                Log.e(TAG, "Error registering button event listener", e)
                OptionalTelemetry.recordException(e)
                errorCallback(SpenRemote.Error.UNSUPPORTED_DEVICE)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering button event listener", e)
                OptionalTelemetry.recordException(e)
                errorCallback(SpenRemote.Error.UNKNOWN)
            }

            Log.i(TAG, "Air motion event listener registered")
        }
    }

    fun unregisterAirMotionEventListener() {
        isAirMotionRegistered = false

        Log.d(TAG, "Unregistering air motion event listener")
        sPenUnitManager?.let {
            val airMotionUnit = it.getUnit(SpenUnit.TYPE_AIR_MOTION)
            it.unregisterSpenEventListener(airMotionUnit)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from S-Pen...")
        ++connectionGeneration
        isSPenButtonDown = false
        onConnectionLost = null
        isAirMotionRegistered = false

        val manager = sPenUnitManager
        sPenUnitManager = null

        try {
            SpenRemote.getInstance().setConnectionStateChangeListener(null)
            manager?.let {
                it.unregisterSpenEventListener(it.getUnit(SpenUnit.TYPE_BUTTON))
                it.unregisterSpenEventListener(it.getUnit(SpenUnit.TYPE_AIR_MOTION))
            }
            SpenRemote.getInstance().disconnect(activityProvider.getActivity())
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error disconnecting from S-Pen, probably activity destroyed before disconnect",
                e
            )
            OptionalTelemetry.recordException(e)
        }
    }

    companion object {
        private const val TAG = "SPenManager"

        fun mapConnectionError(errorCode: Int) = when (errorCode) {
            SpenRemote.Error.UNSUPPORTED_DEVICE -> "Unsupported device"
            SpenRemote.Error.CONNECTION_FAILED -> "Connection failed"
            else -> "Unknown error"
        }

        fun isSPenSupported() = try {
            val sPenClass = Class.forName("com.samsung.android.sdk.penremote.SpenRemote")
            val sPenRemote = sPenClass.getMethod("getInstance").invoke(null)

            val isButtonEnabled = sPenClass.getMethod("isFeatureEnabled", Int::class.java)
                .invoke(sPenRemote, SpenRemote.FEATURE_TYPE_BUTTON) as Boolean
            val isAirMotionEnabled = sPenClass.getMethod("isFeatureEnabled", Int::class.java)
                .invoke(sPenRemote, SpenRemote.FEATURE_TYPE_AIR_MOTION) as Boolean

            if (isButtonEnabled && isAirMotionEnabled) {
                Log.i(TAG, "S-Pen is supported")
                true
            } else {
                Log.w(TAG, "S-Pen is not supported")
                false
            }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "SpenRemote class not found, S-Pen features unavailable", e)
            OptionalTelemetry.recordException(e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if S-Pen is supported", e)
            OptionalTelemetry.recordException(e)
            false
        }
    }
}
