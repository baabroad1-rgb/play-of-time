package ru.iflex.timemvp.core.utils

/**
 * Cheer-Up bubble trigger types.
 *
 * Post-event triggers only. In-session pattern hints live in the Error-Pattern chip
 * beneath the stopwatch button (see ErrorPattern.kt), not here.
 *
 * Success-side:
 *   - AFTER_QUEST               — baseline quest cleared first-try (no subquest detour)
 *   - AFTER_LEVEL               — last quest of the level cleared → level up
 *   - AFTER_GAME                — final quest (L3.5) cleared → game over
 *   - AFTER_SUBQUEST_TO_BASELINE — subquest cleared with baseline-quality result
 *                                  (Rule 2) → jump straight to baseline + advance
 *   - AFTER_SIMPLIFIED_QUEST_DONE — walked back through the subquest ladder and
 *                                   finally took the baseline quest clean
 *
 * Subquest-side (failure / partial progress):
 *   - AFTER_SUBQUEST_ENTER      — first slip on baseline, entering subquest
 *   - AFTER_SUBQUEST_DEEPER     — already in subquest, another step of relief
 *   - AFTER_SUBQUEST_CAPPED     — reached maximum relief (N=99 or t=3 ms) — first time
 *   - AFTER_SUBQUEST_STALLED    — at cap, another cycle exhausted without a win
 *   - AFTER_SUBQUEST_STEPBACK   — subquest win but not baseline-quality (Rule 3),
 *                                 one step tighter, still not baseline
 *
 * Session end uses the yellow SessionSummaryOverlay, not a cheer-up bubble;
 * AFTER_SESSION stays defined but is never fired.
 */
enum class CheerUpTrigger {
    NONE,
    AFTER_SESSION,
    AFTER_QUEST,
    AFTER_LEVEL,
    AFTER_GAME,
    AFTER_SUBQUEST_ENTER,
    AFTER_SUBQUEST_DEEPER,
    AFTER_SUBQUEST_CAPPED,
    AFTER_SUBQUEST_STALLED,
    AFTER_SUBQUEST_STEPBACK,
    AFTER_SUBQUEST_TO_BASELINE,
    AFTER_SIMPLIFIED_QUEST_DONE,
}
