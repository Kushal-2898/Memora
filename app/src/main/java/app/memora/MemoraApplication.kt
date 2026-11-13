package app.memora

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import app.memora.di.AppContainer
import app.memora.di.DefaultAppContainer

/**
 * Application class providing the application composition root.
 */
class MemoraApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        app.memora.platform.logging.AppLog.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "CREATED")
            }
            override fun onActivityStarted(activity: android.app.Activity) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "STARTED")
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "RESUMED")
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "PAUSED")
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "STOPPED")
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                app.memora.platform.logging.AppLog.recordActivityLifecycle(activity.javaClass.simpleName, "DESTROYED")
            }
        })
        container = DefaultAppContainer(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Memora Reminders"
            val descriptionText = "Notifications for active visual memories and due dates"
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(REMINDER_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "memora_reminders"
    }
}
