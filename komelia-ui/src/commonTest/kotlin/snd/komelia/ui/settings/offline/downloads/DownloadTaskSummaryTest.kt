package snd.komelia.ui.settings.offline.downloads

import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun task(index: Int, status: TaskStatus) = TaskEntry(
        task = DownloadBook(KomgaBookId("book-$index")),
        status = status,
    )
}
