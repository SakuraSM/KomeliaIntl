package snd.komelia.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class OnnxModelArchiveTest {
    @Test
    fun incompleteArchiveLeavesTheInstalledModelsAndDestinationsUntouched() = runTest {
        withTemporaryDirectory { root ->
            val destination = root.resolve("installed").createDirectories()
            destination.resolve("mono.onnx").writeText("original")
            destination.resolve("color.onnx").writeText("original color")
            val target = OnnxModelArchive(destination, setOf("mono.onnx"), setOf("color.onnx"))
            assertFailsWith<IllegalStateException> {
                target.install { writeArchive(it, "nested/mono.onnx" to "replacement") }
            }

            assertEquals("original", destination.resolve("mono.onnx").readText())
            assertEquals("original color", destination.resolve("color.onnx").readText())
            assertEquals(listOf(destination), root.listDirectoryEntries())
        }
    }

    @Test
    fun aRetryPublishesTheCompleteSetTogether() = runTest {
        withTemporaryDirectory { root ->
            val destination = root.resolve("installed").createDirectories()
            destination.resolve("mono.onnx").writeText("original")
            destination.resolve("old-color.onnx").writeText("original color")
            val target = OnnxModelArchive(destination, setOf("mono.onnx"), setOf("old-color.onnx", "new-color.onnx"))
            target.install {
                writeArchive(it, "mono.onnx" to "updated", "nested/new-color.onnx" to "updated color")
            }

            assertEquals("updated", destination.resolve("mono.onnx").readText())
            assertEquals("updated color", destination.resolve("new-color.onnx").readText())
            assertFalse(Files.exists(destination.resolve("old-color.onnx")))
        }
    }

    @Test
    fun panelModelReplacementKeepsOtherFilesInTheAppDirectory() = runTest {
        withTemporaryDirectory { root ->
            val destination = root.resolve("installed").createDirectories()
            destination.resolve("panel.onnx").writeText("original")
            destination.resolve("other.onnx").writeText("separate model")
            val target = OnnxModelArchive(destination, setOf("panel.onnx"))
            target.install {
                writeArchive(it, "nested/panel.onnx" to "updated", "unknown.onnx" to "not used")
            }

            assertEquals("updated", destination.resolve("panel.onnx").readText())
            assertEquals("separate model", destination.resolve("other.onnx").readText())
            assertFalse(Files.exists(destination.resolve("unknown.onnx")))
        }
    }

    @Test
    fun cancellingDuringDownloadRemovesThePartialArchiveAndKeepsTheExistingModel() = runTest {
        withTemporaryDirectory { root ->
            val destination = root.resolve("installed").createDirectories()
            destination.resolve("panel.onnx").writeText("original")
            val target = OnnxModelArchive(destination, setOf("panel.onnx"))
            val receivedBytes = CompletableDeferred<Path>()
            val download = launch {
                target.install {
                    it.writeText("partial")
                    receivedBytes.complete(it)
                    awaitCancellation()
                }
            }

            val partialArchive = receivedBytes.await()
            download.cancelAndJoin()
            assertFalse(Files.exists(partialArchive))
            assertEquals(listOf(destination), root.listDirectoryEntries())
            assertEquals("original", destination.resolve("panel.onnx").readText())
        }
    }

    private suspend fun withTemporaryDirectory(test: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory("komelia-onnx-model-test-")
        try {
            test(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun writeArchive(archive: Path, vararg entries: Pair<String, String>) {
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }
}
