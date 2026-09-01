package snd.komelia.ui.settings.offline.downloads

import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadTaskSummaryTest {
    @Test
    fun summarizesLargeQueuesWithoutTreatingCanceledTasksAsRemaining() {
        val tasks = buildList {
            repeat(64) { add(task(it, TaskStatus.COMPLETED)) }
            repeat(20) { add(task(64 + it, TaskStatus.RUNNING)) }
            repeat(8) { add(task(84 + it, TaskStatus.NEW)) }
            repeat(5) { add(task(92 + it, TaskStatus.FAILED)) }
            repeat(3) { add(task(97 + it, TaskStatus.CANCELED)) }
        }

        assertEquals(
            DownloadTaskSummary(total = 100, completed = 64, remaining = 33, failed = 5),
            summarizeDownloadTasks(tasks),
        )
    }

    @Test
    fun filtersActiveFailedAndHistoricalTasksWithoutOverlap() {
        val tasks = TaskStatus.entries.mapIndexed(::task)

        assertEquals(
            listOf(TaskStatus.NEW, TaskStatus.RUNNING, TaskStatus.PAUSED),
            filterDownloadTasks(tasks, DownloadTaskFilter.ACTIVE).map(TaskEntry::status),
        )
        assertEquals(
            listOf(TaskStatus.FAILED),
            filterDownloadTasks(tasks, DownloadTaskFilter.FAILED).map(TaskEntry::status),
        )
        assertEquals(
            listOf(TaskStatus.COMPLETED, TaskStatus.CANCELED),
            filterDownloadTasks(tasks, DownloadTaskFilter.HISTORY).map(TaskEntry::status),
        )
        assertEquals(tasks, filterDownloadTasks(tasks, DownloadTaskFilter.ALL))
    }

    @Test
    fun paginatesLargeQueuesAndClampsStalePageAfterDeletion() {
        val tasks = List(45) { task(it, TaskStatus.COMPLETED) }

        val secondPage = paginateDownloadTasks(tasks, requestedPage = 2)
        assertEquals(3, secondPage.totalPages)
        assertEquals(2, secondPage.currentPage)
        assertEquals(20, secondPage.items.size)

        val lastPage = paginateDownloadTasks(tasks, requestedPage = 99)
        assertEquals(3, lastPage.currentPage)
        assertEquals(5, lastPage.items.size)

        val afterDeletion = paginateDownloadTasks(tasks.take(19), requestedPage = lastPage.currentPage)
        assertEquals(1, afterDeletion.currentPage)
        assertEquals(19, afterDeletion.items.size)
    }

    @Test
    fun emptyQueueStillHasStableFirstPage() {
        val page = paginateDownloadTasks(emptyList(), requestedPage = 3)

        assertEquals(1, page.currentPage)
        assertEquals(1, page.totalPages)
        assertTrue(page.items.isEmpty())
    }

    private fun task(index: Int, status: TaskStatus) = TaskEntry(
        task = DownloadBook(KomgaBookId("book-$index")),
        status = status,
    )
}
