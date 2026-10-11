package snd.komelia.image

import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleImageRequestsConcurrencyTest {
    @Test fun concurrentCallersPublishRequestAndVersionTogether() {
        val requests = VisibleImageRequests<Int>()
        val barrier = CyclicBarrier(5)
        val workers = (0..3).map { worker -> thread {
            barrier.await()
            repeat(50_000) { requests.submit(worker * 50_000 + it) }
        } }
        barrier.await()
        workers.forEach { it.join() }
        assertEquals(200_000L, requests.serial)
        assertEquals(requests.value, requests.state.replayCache.single())
        assertEquals(requests.serial, requests.state.value?.serial)
    }
}
