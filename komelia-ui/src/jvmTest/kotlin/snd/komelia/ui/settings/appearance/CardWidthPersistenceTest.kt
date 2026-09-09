package snd.komelia.ui.settings.appearance

import androidx.compose.ui.unit.dp
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.settings.CommonSettingsRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CardWidthPersistenceTest {
    @Test fun slowerEarlierSaveCannotOverwriteTheLastSliderChoice() = verifySave(false)
    @Test fun leavingSettingsWhileSavingStillPersistsTheLastChoice() = verifySave(true)

    private fun verifySave(leaveScreen: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val writes = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var stored = 240
        var first = true
        lateinit var pending: Continuation<Unit>
        val repository = Proxy.newProxyInstance(CommonSettingsRepository::class.java.classLoader,
            arrayOf(CommonSettingsRepository::class.java)) { _, method, args ->
            check(method.name.startsWith("putCardWidth"))
            if (first) {
                first = false
                @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<Unit>
                COROUTINE_SUSPENDED
            } else {
                stored = args.first() as Int
                Unit
            }
        } as CommonSettingsRepository
        try {
            val vm = AppSettingsViewModel(repository, writeScope = writes)
            vm.onCardWidthChange(180.dp)
            advanceUntilIdle()
            vm.onCardWidthChange(350.dp)
            advanceUntilIdle()
            if (leaveScreen) writes.cancel()
            stored = 180
            pending.resume(Unit)
            advanceUntilIdle()
            assertEquals(350, stored)
            assertEquals(350.dp, vm.cardWidth)
        } finally {
            writes.cancel()
            Dispatchers.resetMain()
        }
    }
}
