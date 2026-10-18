package pl.jojczak.penmouses

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import pl.jojczak.penmouses.core.common.di.ActivityProvider
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import javax.inject.Inject

@HiltAndroidApp
class PenMouseSApp : Application() {
    @Inject
    lateinit var activityProvider: ActivityProvider

    override fun onCreate() {
        super.onCreate()
        DiagnosticRecorder.initialize(this)
        registerActivityLifecycleCallbacks(activityProvider)
    }
}