package snd.komelia.ui.common.menus

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookReadProgressMutationTest {
    @Test
    fun successfulMutationNotifiesTheCurrentScreen() = runTest {
        val events = mutableListOf<String>()

        runBookReadProgressMutation(
            mutation = { events += "mutation" },
            onSuccess = { events += "success" },
        )

        assertEquals(listOf("mutation", "success"), events)
    }

    @Test
    fun failedMutationDoesNotNotifyTheCurrentScreen() = runTest {
        val events = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            runBookReadProgressMutation(
                mutation = {
                    events += "mutation"
                    error("failed")
                },
                onSuccess = { events += "success" },
            )
        }

        assertEquals(listOf("mutation"), events)
    }
}
