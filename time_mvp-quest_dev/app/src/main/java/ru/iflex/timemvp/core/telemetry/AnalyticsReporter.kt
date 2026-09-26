package ru.iflex.timemvp.core.telemetry

/**
 * Analytics facade — swap the Koin binding (KoinModule.kt) for a Firebase-backed
 * implementation once google-services.json is available. Call sites never change,
 * only the binding does.
 */
interface AnalyticsReporter {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
}

/** Default binding — inert until a real backend is wired in. */
class NoOpAnalyticsReporter : AnalyticsReporter {
    override fun logEvent(name: String, params: Map<String, Any?>) {
        // Intentionally does nothing — see class doc.
    }
}
