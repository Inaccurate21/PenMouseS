package pl.jojczak.penmouses.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import pl.jojczak.penmouses.core.common.notifications.NotificationsManager
import pl.jojczak.penmouses.core.common.utils.DiagnosticRecorder
import pl.jojczak.penmouses.core.common.spen.AppToServiceEvent
import pl.jojczak.penmouses.core.common.spen.SPenManager
import pl.jojczak.penmouses.core.ui.theme.PenMouseSTheme
import pl.jojczak.penmouses.navigation.PenMouseSContentWithManualDrawer
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sPenManager: SPenManager

    @Inject
    lateinit var notificationsManager: NotificationsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreenHelper = SplashScreenHelper(this, installSplashScreen(), savedInstanceState == null)
        super.onCreate(savedInstanceState)
        DiagnosticRecorder.record("activity", "onCreate")
        enableEdgeToEdge()
        setContent { AppContent() }
        splashScreenHelper.startExitAnimation()
        notificationsManager.createNotificationChannels(this)
    }

    override fun onStart() {
        super.onStart()
        DiagnosticRecorder.record("activity", "foreground")
    }

    override fun onStop() {
        DiagnosticRecorder.record("activity", "background (mouse mode may continue)")
        super.onStop()
    }

    override fun onDestroy() {
        DiagnosticRecorder.record("activity", "destroy changingConfigurations=$isChangingConfigurations")
        Log.d(TAG, "onDestroy")
        // Mouse mode is owned by the accessibility service, not this activity.
        // Keep an active session alive when the user closes the UI or uses the QS tile.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun AppContent() {
    PenMouseSTheme {
        PenMouseSContentWithManualDrawer()
    }
}