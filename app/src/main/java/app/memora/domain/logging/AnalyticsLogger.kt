package app.memora.domain.logging

interface AnalyticsLogger {
    fun logEvent(eventName: String, params: Map<String, String>? = null)
    fun setUserProperty(name: String, value: String)
}
