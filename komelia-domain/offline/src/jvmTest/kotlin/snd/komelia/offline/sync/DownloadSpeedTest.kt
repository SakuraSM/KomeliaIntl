package snd.komelia.offline.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSpeedTest {
    @Test
    fun calculatesBytesPerSecondFromAStableSample() {
        assertEquals(2_000L, calculateDownloadSpeed(bytesDelta = 1_000L, elapsedMillis = 500L))
    }

    @Test
    fun rejectsEmptyAndRegressingSamples() {
        assertEquals(0L, calculateDownloadSpeed(bytesDelta = 0L, elapsedMillis = 500L))
        assertEquals(0L, calculateDownloadSpeed(bytesDelta = -1L, elapsedMillis = 500L))
        assertEquals(0L, calculateDownloadSpeed(bytesDelta = 1_000L, elapsedMillis = 0L))
    }
}
