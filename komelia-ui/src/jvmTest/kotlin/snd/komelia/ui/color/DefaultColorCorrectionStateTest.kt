package snd.komelia.ui.color

import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.AppNotifications
import snd.komelia.color.*
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.ui.settings.imagereader.DefaultColorCorrectionDependencies
import snd.komelia.ui.settings.imagereader.DefaultColorCorrectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultColorCorrectionStateTest {
    private val choice = DefaultColorCorrection("Gamma", ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS))

    @Test fun leavingDuringASaveFinishesTheWriteAndPublishesIt() = runTest {
        val stored = MutableStateFlow<DefaultColorCorrection?>(null)
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val state = state(scope, stored) { value -> release.await(); stored.value = value }
        state.initialize()
        state.select(choice)
        runCurrent()
        assertTrue(state.isSaving.value)
        scope.cancel()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(choice, stored.value)
        assertEquals(choice, state.selected.value)
        assertFalse(state.isSaving.value)
    }

    @Test fun failedSaveDoesNotPublishAnUnsavedSelection() = runTest {
        val stored = MutableStateFlow<DefaultColorCorrection?>(choice)
        val state = state(backgroundScope, stored) { throw IOException("synthetic write failure") }
        state.initialize()
        state.select(null)
        runCurrent()
        assertEquals(choice, stored.value)
        assertEquals(choice, state.selected.value)
        assertFalse(state.isSaving.value)
    }

    private fun state(
        scope: CoroutineScope,
        stored: MutableStateFlow<DefaultColorCorrection?>,
        save: suspend (DefaultColorCorrection?) -> Unit,
    ): DefaultColorCorrectionState {
        val settings = Proxy.newProxyInstance(ImageReaderSettingsRepository::class.java.classLoader,
            arrayOf(ImageReaderSettingsRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getDefaultColorCorrection" -> stored
                "putDefaultColorCorrection" -> {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args.last() as Continuation<Unit>
                    val block: suspend () -> Unit = { save(args.first() as DefaultColorCorrection?) }
                    block.startCoroutineUninterceptedOrReturn(continuation)
                }
                else -> error("Unexpected call ${method.name}")
            }
        } as ImageReaderSettingsRepository
        return DefaultColorCorrectionState(DefaultColorCorrectionDependencies(
            settings, stubPresets(), stubPresets(), AppNotifications(),
        ), scope)
    }

    private inline fun <reified T> stubPresets(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ ->
        check(method.name == "getPresets")
        emptyList<Any>()
    } as T
}
