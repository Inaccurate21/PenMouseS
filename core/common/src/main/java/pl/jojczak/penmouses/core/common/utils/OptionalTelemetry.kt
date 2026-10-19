package pl.jojczak.penmouses.core.common.utils

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics

/** No Firebase default app is configured in a normal fork/debug build. */
object OptionalTelemetry {
    fun recordException(error: Throwable) {
        Log.e("PenMouseS", "Operation failed", error)
        runCatching { Firebase.crashlytics.recordException(error) }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { Firebase.analytics.setAnalyticsCollectionEnabled(enabled) }
        runCatching { Firebase.crashlytics.isCrashlyticsCollectionEnabled = enabled }
    }
}
