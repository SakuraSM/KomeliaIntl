package snd.komelia.db.offline.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json
import snd.komelia.db.JsonDbDefault
import snd.komelia.offline.tasks.model.TaskData

object OfflineTaskTable : Table("TASK") {
    val uniqueName = text("unique_name")
    val priority = integer("priority")
    val status = text("status")
    val task = json<TaskData>("task", JsonDbDefault)

    val createdDate = long("created_date")
    val completedBytes = long("completed_bytes").default(0)
    val totalBytes = long("total_bytes").default(0)
    val speedBytesPerSecond = long("speed_bytes_per_second").default(0)
    val displayTitle = text("display_title").nullable()
    val errorMessage = text("error_message").nullable()

    override val primaryKey = PrimaryKey(uniqueName)
}
