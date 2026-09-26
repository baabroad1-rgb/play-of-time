package online.playoftime.core.telemetry

/**
 * Crash-reporting facade — swap the Koin binding (KoinModule.kt) for a
 * Firebase Crashlytics-backed implementation once google-services.json is
 * available. MainApplication already wires a default-uncaught-exception hook
 * through this interface, so activating a real backend needs no call-site changes.
 */
interface CrashReporter {
    fun recordException(t: Throwable)
    fun log(message: String)
}

/** Default binding — inert until a real backend is wired in. */
class NoOpCrashReporter : CrashReporter {
    override fun recordException(t: Throwable) {
        // Intentionally does nothing — see class doc.
    }
    override fun log(message: String) {
        // Intentionally does nothing — see class doc.
    }
}
