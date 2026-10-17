package app.memora.platform.logging

import android.content.Context
import android.os.Bundle
import app.memora.domain.logging.AnalyticsLogger
import com.google.firebase.analytics.FirebaseAnalytics

class FirebaseAnalyticsLogger(context: Context) : AnalyticsLogger {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun logEvent(eventName: String, params: Map<String, String>?) {
        val bundle = params?.let {
            Bundle().apply {
                it.forEach { (key, value) -> putString(key, value) }
            }
        }
        analytics.logEvent(eventName, bundle)
    }

    override fun setUserProperty(name: String, value: String) {
        analytics.setUserProperty(name, value)
    }
}
