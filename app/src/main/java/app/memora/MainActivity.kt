package app.memora

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import app.memora.presentation.navigation.MemoraNavHost
import app.memora.ui.theme.MemoraTheme

class MainActivity : ComponentActivity() {

  private val pendingSharedImageUri = mutableStateOf<String?>(null)
  private val pendingNotificationMemoryId = mutableStateOf<String?>(null)
  private val pendingOpenScanner = mutableStateOf(false)

  private fun handleIntents(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
      val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      if (uri != null) {
        pendingSharedImageUri.value = uri.toString()
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.action = null
      }
    }
    val memoryId = intent.getStringExtra(app.memora.platform.notification.ReminderAlarmReceiver.KEY_MEMORY_ID)
    if (!memoryId.isNullOrBlank()) {
      pendingNotificationMemoryId.value = memoryId
      intent.removeExtra(app.memora.platform.notification.ReminderAlarmReceiver.KEY_MEMORY_ID)
    }
    if (intent.getBooleanExtra(app.memora.platform.notification.ScanNudgeWorker.KEY_OPEN_SCANNER, false)) {
      pendingOpenScanner.value = true
      intent.removeExtra(app.memora.platform.notification.ScanNudgeWorker.KEY_OPEN_SCANNER)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntents(intent)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = androidx.activity.SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
      navigationBarStyle = androidx.activity.SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
      ),
    )
    if (savedInstanceState == null) {
      handleIntents(intent)
    }
    val appContainer = (application as MemoraApplication).container

    setContent {
      MemoraTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          MemoraNavHost(
            appContainer = appContainer,
            sharedImageUri = pendingSharedImageUri.value,
            onSharedImageConsumed = { pendingSharedImageUri.value = null },
            initialMemoryId = pendingNotificationMemoryId.value,
            onMemoryIdConsumed = { pendingNotificationMemoryId.value = null },
            openScannerOnLaunch = pendingOpenScanner.value,
            onOpenScannerConsumed = { pendingOpenScanner.value = false },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

