package app.memora

import app.memora.domain.logging.AnalyticsLogger
import app.memora.domain.logging.CrashReporter

class FakeCrashReporter : CrashReporter {
    override fun recordException(throwable: Throwable) {}
    override fun log(message: String) {}
    override fun setUserId(userId: String) {}
    override fun setCustomKey(key: String, value: String) {}
}

class FakeAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(eventName: String, params: Map<String, String>?) {}
    override fun setUserProperty(name: String, value: String) {}
}
