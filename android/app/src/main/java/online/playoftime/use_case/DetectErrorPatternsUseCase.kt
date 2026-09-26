package online.playoftime.use_case

import online.playoftime.core.utils.ErrorPattern
import kotlin.math.abs

/**
 * Detects the current Error-Pattern chip state from session attempts.
 * Returns the highest-priority pattern that matches; NONE if nothing matches.
 *
 * Thresholds match cheer_ups.docx:
 *   • SYSTEMATIC_EARLY / SYSTEMATIC_LATE — 9 attempts in a row, all early / all late
 *   • INCREASING_DRIFT — 6+ consecutive attempts, |error| strictly grows
 *   • JAGGED_RHYTHM — last 4 attempts alternate sign
 *   • TRIPLE_IDENTICAL — 3 identical errors in a row + a 4th with a different value
 *   • PAUSE_SHORT / PAUSE_LONG — last 3 pauses < or > 300 ms AND last 3 results all early / late
 *
 * @param errors    Signed errors of last attempts in chronological order.
 * @param pausesMs  Pauses between consecutive attempts (ms). `pausesMs[i]` = startTimestamp[i+1] − endTimestamp[i].
 *                  Pass an empty list when timestamps are unknown; pause patterns simply won't fire.
 */
class DetectErrorPatternsUseCase {

    private val pauseThresholdMs = 300L
    private val pauseWindow      = 3
    /** Two errors are considered identical when their difference is below this. */
    private val identicalTolerance = 0.0005

    operator fun invoke(errors: List<Double>, pausesMs: List<Long> = emptyList()): ErrorPattern {
        if (errors.isEmpty()) return ErrorPattern.NONE

        // 1. Systematic early / late — 9 in a row
        if (errors.size >= 9) {
            val last9 = errors.takeLast(9)
            if (last9.all { it < 0.0 }) return ErrorPattern.SYSTEMATIC_EARLY
            if (last9.all { it > 0.0 }) return ErrorPattern.SYSTEMATIC_LATE
        }

        // 2. Increasing drift — 6+ consecutive, |error| strictly grows
        if (errors.size >= 6) {
            val last6 = errors.takeLast(6).map { abs(it) }
            if (last6.zipWithNext().all { (a, b) -> b > a }) return ErrorPattern.INCREASING_DRIFT
        }

        // 2b. Same-colour growing — 3 in a row, all red or all blue, |error| grows.
        //   RED_GROWING  → all early (< 0)  AND  magnitudes increasing → "easy, easy…"
        //   BLUE_GROWING → all late  (> 0)  AND  values  increasing    → "wake up!!!"
        if (errors.size >= 3) {
            val last3 = errors.takeLast(3)
            val growing = last3.zipWithNext().all { (a, b) -> abs(b) > abs(a) }
            if (growing) {
                if (last3.all { it < 0.0 }) return ErrorPattern.RED_GROWING
                if (last3.all { it > 0.0 }) return ErrorPattern.BLUE_GROWING
            }
        }

        // 3. Triple identical — 3 same errors then a different 4th
        if (errors.size >= 4) {
            val last4 = errors.takeLast(4)
            val a = last4[0]; val b = last4[1]; val c = last4[2]; val d = last4[3]
            val tripleMatch = abs(a - b) < identicalTolerance && abs(b - c) < identicalTolerance
            val fourthDiff  = abs(c - d) >= identicalTolerance
            if (tripleMatch && fourthDiff) return ErrorPattern.TRIPLE_IDENTICAL
        }

        // 4. Pause-short / pause-long — 3 last pauses + 3 last errors all early / late
        if (errors.size >= pauseWindow && pausesMs.size >= pauseWindow - 1) {
            val lastErrors = errors.takeLast(pauseWindow)
            val lastPauses = pausesMs.takeLast(pauseWindow - 1)
            if (lastPauses.none { it == 0L }) {
                val allShort = lastPauses.all { it < pauseThresholdMs }
                val allLong  = lastPauses.all { it > pauseThresholdMs }
                val allRed   = lastErrors.all { it < 0.0 }
                val allBlue  = lastErrors.all { it > 0.0 }
                if (allShort && allRed)  return ErrorPattern.PAUSE_SHORT
                if (allLong  && allBlue) return ErrorPattern.PAUSE_LONG
            }
        }

        // 5. Approaching to the balance — last 3 attempts, |error| strictly decreasing.
        //    Any sign pattern qualifies (red→red→red shrinking, alternating shrinking,
        //    etc.) — the player is converging toward 3.000.
        if (errors.size >= 3) {
            val last3 = errors.takeLast(3).map { abs(it) }
            if (last3.zipWithNext().all { (a, b) -> b < a }) return ErrorPattern.JAGGED_RHYTHM
        }

        return ErrorPattern.NONE
    }
}
