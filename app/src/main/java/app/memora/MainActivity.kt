package app.memora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.memora.presentation.navigation.MemoraNavHost
import app.memora.ui.theme.MemoraTheme

class MainActivity : ComponentActivity() {

  private val showNotificationRationale = mutableStateOf(false)

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (!isGranted) {
      showNotificationRationale.value = true
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as MemoraApplication).container

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (appContainer.onboardingPreferences.hasCompletedOnboarding()) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    }

    setContent {
      MemoraTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          MemoraNavHost(
            appContainer = appContainer,
            modifier = Modifier.fillMaxSize(),
          )

          if (showNotificationRationale.value) {
            AlertDialog(
              onDismissRequest = { showNotificationRationale.value = false },
              title = {
                Text(text = stringResource(R.string.notification_permission_title))
              },
              text = {
                Text(text = stringResource(R.string.notification_permission_message))
              },
              confirmButton = {
                TextButton(
                  onClick = {
                    showNotificationRationale.value = false
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                      putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                  }
                ) {
                  Text(text = stringResource(R.string.action_open_settings))
                }
              },
              dismissButton = {
                TextButton(
                  onClick = { showNotificationRationale.value = false }
                ) {
                  Text(text = stringResource(R.string.action_not_now))
                }
              }
            )
          }
        }
      }
    }
  }
}
