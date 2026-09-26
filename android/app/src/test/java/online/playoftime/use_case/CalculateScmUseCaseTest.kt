package online.playoftime.use_case

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import online.playoftime.database.TryDatabaseModel

/**
 * Targeted unit tests for the SCM formula (see CalculateScmUseCase.kt's own
 * doc comment for the source formula from measures.docx / parametr_table.docx).
 *
 * Added 2026-08-13 as part of a gap-analysis pass (plan_remaining_work.md
 * flagged "no automated tests" on the formula-heavy code as a real risk —
 * this is a first, narrow slice of that: the one use case here that's a pure
 * function with zero Android/Room dependencies, so it's cheap to test in a
 * plain JVM unit test (no instrumentation/emulator needed).
 *
 * Run with: ./gradlew testDebugUnitTest --tests "*CalculateScmUseCaseTest*"
 */
class CalculateScmUseCaseTest {

    private val useCase = CalculateScmUseCase()

    /** Builds a minimal TryDatabaseModel; only startTime/endTime/accuracy matter for SCM. */
    private fun attempt(endTime: Long, accuracy: Int, startTime: Long = 0L) = TryDatabaseModel(
        id = 0,
        sessionId = 0,
        startTime = startTime,
        endTime = endTime,
        deflection = null,
        accuracy = accuracy,
    )

    @Test
    fun `zero error on all three attempts peaks at 99 times the level factor`() {
        // prev/curr/next all land exactly on aimTime -> denominator collapses to 1.
        val aimMs = 3000
        val prev = attempt(endTime = 3000, accuracy = 3)
        val next = attempt(endTime = 3000, accuracy = 3)

        val l3 = useCase(prev, attempt(endTime = 3000, accuracy = 3), next, aimMs)
        val l2 = useCase(prev, attempt(endTime = 3000, accuracy = 2), next, aimMs)
        val l1 = useCase(prev, attempt(endTime = 3000, accuracy = 1), next, aimMs)

        // Documented peaks (CalculateScmUseCase.kt header comment): L1->0.99, L2->9.9, L3->99.
        assertEquals(99.0, l3, 1e-9)
        assertEquals(9.9, l2, 1e-9)
        assertEquals(0.99, l1, 1e-9)
    }

    @Test
    fun `known asymmetric case matches hand-calculated value`() {
        // Hand-derived in Python (same formula, double precision) before writing this
        // assertion, so this is an independent check, not a copy of the Kotlin math:
        //   prev=3010ms (err 10), curr=2995ms (err 5), next=3020ms (err 20), aim=3000ms
        //   denom = 3*5 + 10 + 20 + |2995-3020| + |2995-3010| - |3020-3010|/3 + 1
        //         = 15 + 10 + 20 + 25 + 15 - 3.3333... + 1 = 82.666...
        //   scm (L3, levelFactor=1) = 99 / 82.6666... = 1.1975806451612903
        val prev = attempt(endTime = 3010, accuracy = 3)
        val curr = attempt(endTime = 2995, accuracy = 3)
        val next = attempt(endTime = 3020, accuracy = 3)

        val scm = useCase(prev, curr, next, aimTimeMs = 3000)

        assertEquals(1.1975806451612903, scm, 1e-9)
    }

    @Test
    fun `larger current-attempt error yields a lower SCM, neighbors held fixed`() {
        val aimMs = 3000
        val prev = attempt(endTime = 3005, accuracy = 3)
        val next = attempt(endTime = 2996, accuracy = 3)

        val closeCall = useCase(prev, attempt(endTime = 3002, accuracy = 3), next, aimMs)
        val wideMiss = useCase(prev, attempt(endTime = 3050, accuracy = 3), next, aimMs)

        assertTrue("a tighter current-attempt error should score a higher SCM", closeCall > wideMiss)
    }

    @Test
    fun `level factor scales SCM down by exactly 10x per level, same raw error`() {
        val aimMs = 3000
        val prev = attempt(endTime = 3004, accuracy = 3)
        val curr = attempt(endTime = 2997, accuracy = 3)
        val next = attempt(endTime = 3006, accuracy = 3)

        val l3 = useCase(prev, curr.copy(accuracy = 3), next, aimMs)
        val l2 = useCase(prev, curr.copy(accuracy = 2), next, aimMs)
        val l1 = useCase(prev, curr.copy(accuracy = 1), next, aimMs)

        assertEquals(l3 / 10.0, l2, 1e-9)
        assertEquals(l2 / 10.0, l1, 1e-9)
    }
}
