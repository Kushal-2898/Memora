package app.memora.domain.logging

interface CrashReporter {
    fun recordException(throwable: Throwable)
    fun log(message: String)
    fun setUserId(userId: String)
    fun setCustomKey(key: String, value: String)
}
