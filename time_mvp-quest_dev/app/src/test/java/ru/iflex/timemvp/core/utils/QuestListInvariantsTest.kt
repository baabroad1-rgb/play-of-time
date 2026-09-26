package ru.iflex.timemvp.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Targeted unit tests for `questList` — pure data, zero Android/Room dependencies,
 * so cheap to check in a plain JVM unit test.
 *
 * Added 2026-08-13 as part of a gap-analysis pass. This list has already changed
 * shape twice recently (toleranceStepsCap raised for L3.4/L3.5 on 2026-08-08; the
 * web app briefly drifted from it — see TASKS.md P0-3) — these tests exist to catch
 * the next such change automatically instead of relying on someone noticing by hand.
 *
 * Encodes the "Indulgence: only ONE adaptation at a time" rule from webapp/CLAUDE.md's
 * non-negotiable invariants and the in-file header comment in Quest.kt (Alex 2026-06-23).
 *
 * Run with: ./gradlew testDebugUnitTest --tests "*QuestListInvariantsTest*"
 */
class QuestListInvariantsTest {

    @Test
    fun `exactly 15 quests, 5 per level across 3 levels`() {
        assertEquals(15, questList.size)
        for (level in 1..3) {
            assertEquals("level $level should have exactly 5 quests", 5, questList.count { it.levelId == level })
        }
    }

    @Test
    fun `each level's task IDs are exactly 1 through 5, no gaps or dupes`() {
        for (level in 1..3) {
            val taskIds = questList.filter { it.levelId == level }.map { it.taskId }.sorted()
            assertEquals(listOf(1, 2, 3, 4, 5), taskIds)
        }
    }

    @Test
    fun `never both adaptiveN and adaptiveT at once, on any quest`() {
        // This is the "only one kind of indulgence at a time" invariant called out in
        // webapp/CLAUDE.md and in Quest.kt's own header comment — worth guarding directly.
        questList.forEach { quest ->
            assertFalse(
                "L${quest.levelId}.${quest.taskId} has both adaptiveN and adaptiveT set",
                quest.adaptiveN && quest.adaptiveT
            )
        }
    }

    @Test
    fun `adaptiveN is only ever set on IN_N victory-condition quests`() {
        questList.filter { it.adaptiveN }.forEach { quest ->
            assertEquals(
                "L${quest.levelId}.${quest.taskId} has adaptiveN=true but isn't an IN_N quest",
                VictoryCondition.IN_N, quest.victory.victoryCondition
            )
        }
    }

    @Test
    fun `adaptiveT is only ever set on ROW victory-condition quests`() {
        questList.filter { it.adaptiveT }.forEach { quest ->
            assertEquals(
                "L${quest.levelId}.${quest.taskId} has adaptiveT=true but isn't a ROW quest",
                VictoryCondition.ROW, quest.victory.victoryCondition
            )
        }
    }

    @Test
    fun `toleranceStepsCap is 3 everywhere except L3-4 and L3-5, which are 9`() {
        questList.forEach { quest ->
            val isRaisedCapQuest = quest.levelId == 3 && quest.taskId in listOf(4, 5)
            val expectedCap = if (isRaisedCapQuest) 9 else 3
            assertEquals(
                "L${quest.levelId}.${quest.taskId} toleranceStepsCap",
                expectedCap, quest.toleranceStepsCap
            )
        }
    }

    @Test
    fun `accuracy matches the quest's level (L1 deci, L2 centi, L3 milli)`() {
        val expected = mapOf(1 to Accuracy.DECISECONDS, 2 to Accuracy.CENTISECONDS, 3 to Accuracy.MILLISECONDS)
        questList.forEach { quest ->
            assertEquals(
                "L${quest.levelId}.${quest.taskId} accuracy",
                expected.getValue(quest.levelId), quest.accuracy
            )
        }
    }

    @Test
    fun `adaptiveNStep is currently 33 on every quest (no per-quest override wired up)`() {
        // NOTE: Quest.kt's class-level doc comment says "по умолчанию 33; квест 3.4 = 1",
        // implying L3.4 was meant to override this — but as of 2026-08-13 no quest in
        // questList actually sets adaptiveNStep, so every quest uses the 33 default. This
        // test pins today's actual behavior; if that comment's intent ever gets implemented,
        // this test (and the stale comment) should be updated together, not just the comment.
        questList.forEach { quest ->
            assertEquals(
                "L${quest.levelId}.${quest.taskId} adaptiveNStep",
                33, quest.adaptiveNStep
            )
        }
    }

    @Test
    fun `exactly these 5 quests have no attempt-window limit`() {
        // maxAttemptsDefault == null on: the three ONE_ONLY quests (L1.1/L2.1/L3.1, one per
        // level) plus L1.4/L1.5 (level 1's ROW quests have no indulgence mechanism at all,
        // unlike their L2/L3 counterparts which gained adaptiveT + a 33-attempt window).
        // Encoded as an explicit set rather than a general rule, since L1's ROW quests are a
        // deliberate exception to the "ROW quests have a 33-attempt window" pattern used from
        // L2.4 onward — worth pinning exactly, so a future change here is a conscious edit,
        // not an accidental one.
        val unlimited = questList.filter { it.maxAttemptsDefault == null }
            .map { "${it.levelId}.${it.taskId}" }
            .toSet()
        assertEquals(setOf("1.1", "1.4", "1.5", "2.1", "3.1"), unlimited)
    }
}
