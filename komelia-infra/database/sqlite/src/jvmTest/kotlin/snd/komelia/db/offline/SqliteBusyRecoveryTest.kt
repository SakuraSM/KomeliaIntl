package snd.komelia.db.offline

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.Test
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import snd.komelia.db.ExposedTransactionTemplate
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.offline.dto.ExposedOfflineBookDtoRepository
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.common.KomgaPageRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SqliteBusyRecoveryTest {
    @Test fun aWriteLockFailureDoesNotPoisonTheNextTransaction() = runBlocking {
        val directory = Files.createTempDirectory("komelia-busy-recovery-")
        val database = KomeliaDatabase(directory.toString())
        val repository = ExposedOfflineBookDtoRepository(database.offline)
        val transaction = ExposedTransactionTemplate(database.offline)
        DriverManager.getConnection("jdbc:sqlite:$directory/offline.sqlite").use { blocker ->
            blocker.createStatement().use { statement ->
                statement.execute("BEGIN IMMEDIATE")
                try {
                    val failure = assertFails {
                        transaction.execute { repository.findAll(OfflineUser.ROOT, KomgaPageRequest(unpaged = true)) }
                    }
                    assertTrue(generateSequence(failure as Throwable?) { it.cause }.any {
                        it is SQLiteException && it.resultCode == SQLiteErrorCode.SQLITE_BUSY
                    })
                } finally {
                    statement.execute("ROLLBACK")
                }
            }
        }
        val result = transaction.execute { repository.findAll(OfflineUser.ROOT, KomgaPageRequest(unpaged = true)) }
        assertEquals(0, result.totalElements)

        suspendTransaction(db = database.offline) {
            exec("CREATE TABLE recovery_test (value INTEGER NOT NULL)")
            exec("INSERT INTO recovery_test VALUES (1)")
        }
        assertFails {
            suspendTransaction(db = database.offline) {
                exec("INSERT INTO recovery_test VALUES (2)")
                error("abort transaction")
            }
        }
        DriverManager.getConnection("jdbc:sqlite:$directory/offline.sqlite").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT value FROM recovery_test").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                    assertFalse(rows.next())
                }
            }
        }
    }
}
