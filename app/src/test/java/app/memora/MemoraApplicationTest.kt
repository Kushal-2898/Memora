package app.memora

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.memora.platform.notification.MemoraNotificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemoraApplicationTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Memora", appName)
  }

  @Test
  fun `application initializes dependency container successfully`() {
    val app = ApplicationProvider.getApplicationContext<MemoraApplication>()
    assertNotNull(app.container)
    assertNotNull(app.container.database)
    assertNotNull(app.container.memoryRepository)
    assertNotNull(app.container.reminderRepository)
    assertNotNull(app.container.clockProvider)
    assertNotNull(app.container.textExtractor)
    assertNotNull(app.container.memoryExtractor)
    assertNotNull(app.container.notificationScheduler)
    assertTrue(app.container.notificationScheduler is MemoraNotificationScheduler)
  }

  @Test
  fun `application creates memora_reminders notification channel`() {
    val app = ApplicationProvider.getApplicationContext<MemoraApplication>()
    val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = notificationManager.getNotificationChannel("memora_reminders")
    assertNotNull(channel)
    assertEquals("Memora Reminders", channel.name)
  }
}
