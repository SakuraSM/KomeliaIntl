package snd.komelia.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.ui.common.components.GlassSamplingBudget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GlassScrollBudgetTest {
    @Test fun captureResumesOnlyAfterTheLastScrollSettles() = runTest {
        val budget = GlassSamplingBudget(this)
        val page = Any()
        val child = Any()
        budget.begin(page)
        budget.begin(child)
        budget.end(page)
        advanceTimeBy(500)
        assertTrue(budget.isPaused)
        budget.end(child)
        advanceTimeBy(249)
        assertTrue(budget.isPaused)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(budget.isPaused)
    }

    @Test fun consecutiveGesturesDoNotReallocateCaptureBetweenDrags() = runTest {
        val budget = GlassSamplingBudget(this)
        val owner = Any()
        budget.begin(owner)
        budget.end(owner)
        advanceTimeBy(200)
        budget.begin(owner)
        advanceTimeBy(100)
        assertTrue(budget.isPaused)
        budget.end(owner)
        advanceTimeBy(250)
        runCurrent()
        assertFalse(budget.isPaused)
    }

    @Test fun disposingAnIdleSiblingDoesNotResumeAnActiveScroll() = runTest {
        val budget = GlassSamplingBudget(this)
        val owner = Any()
        budget.begin(owner)
        budget.end(Any())
        advanceTimeBy(500)
        assertTrue(budget.isPaused)
        budget.end(owner)
        advanceTimeBy(250)
        runCurrent()
        assertFalse(budget.isPaused)
    }
}
