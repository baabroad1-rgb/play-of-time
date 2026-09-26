package ru.iflex.timemvp.core.utils

/**
 * Detected pattern in the recent attempts of the current session.
 * Rendered as the orange/yellow chip beneath the stopwatch button.
 * Patterns 5–7 (TRIPLE_IDENTICAL, PAUSE_SHORT, PAUSE_LONG) used to be in-session
 * cheer-up bubbles per cheer_ups.docx; they live here now.
 */
enum class ErrorPattern {
    NONE,
    SYSTEMATIC_EARLY,   // 9 attempts in a row early
    SYSTEMATIC_LATE,    // 9 attempts in a row late
    INCREASING_DRIFT,   // 6+ consecutive attempts, |error| strictly grows
    JAGGED_RHYTHM,      // last 4 attempts alternate sign
    TRIPLE_IDENTICAL,   // 3 identical errors in a row + a 4th with a different value
    PAUSE_SHORT,        // last 3 attempts: pauses < 300 ms AND all results < 3 s
    PAUSE_LONG,         // last 3 attempts: pauses > 300 ms AND all results > 3 s
    RED_GROWING,        // last 3 attempts: all early AND |error| strictly grows  → "easy, easy…"
    BLUE_GROWING        // last 3 attempts: all late  AND  error  strictly grows  → "wake up!!!"
}
