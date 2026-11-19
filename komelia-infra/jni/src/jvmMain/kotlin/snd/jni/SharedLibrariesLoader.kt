package snd.jni

import org.slf4j.LoggerFactory
import snd.jni.SharedLibrariesLoader.LibraryLoadResult.BundledLibrary
import snd.jni.SharedLibrariesLoader.LibraryLoadResult.SystemLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object SharedLibrariesLoader {
    private val logger = LoggerFactory.getLogger(SharedLibrariesLoader::class.java)
    private val composeResourcesDir = System.getProperty("compose.application.resources.dir")?.let { Path(it) }
    val tempDir: Path = Path(System.getProperty("java.io.tmpdir"))
        .resolve("komelia")
        .resolve("libs")
        .createDirectories()
    private var macLibrariesDir: Path? = null

    /** Extract the complete macOS dependency set before loading any library in it. */
    @Synchronized
    @Suppress("UnsafeDynamicallyLoadedCode")
    fun loadMacLibrary(libName: String): LibraryLoadResult {
        val architecture = when (System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> throw UnsatisfiedLinkError("Unsupported macOS architecture")
        }
        val resourcePath = "/macos/$architecture"
        val manifest = SharedLibrariesLoader::class.java.getResourceAsStream("$resourcePath/manifest.txt")
            ?: return loadLibrary(libName)
        val directory = macLibrariesDir ?: run {
            val filenames = manifest.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
            check(filenames.isNotEmpty() && filenames.distinct().size == filenames.size &&
                filenames.all { it.matches(Regex("[A-Za-z0-9_.+-]+\\.dylib")) }) { "Invalid macOS native library manifest" }
            val destination = Files.createTempDirectory(tempDir, "macos-")
            destination.toFile().deleteOnExit()
            for (filename in filenames) {
                val target = destination.resolve(filename)
                val source = SharedLibrariesLoader::class.java.getResourceAsStream("$resourcePath/$filename")
                    ?: error("Missing bundled macOS library $filename")
                source.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
                target.toFile().deleteOnExit()
            }
            destination.also { macLibrariesDir = it }
        }
        manifest.close()
        val file = directory.resolve(System.mapLibraryName(libName))
        if (!file.exists()) throw UnsatisfiedLinkError("Missing bundled macOS library ${file.fileName}")
        System.load(file.absolutePathString())
        logger.info("loaded bundled macOS native library {}", file.fileName)
        return BundledLibrary
    }


    @Suppress("UnsafeDynamicallyLoadedCode")
    fun findAndLoadFile(filename: String) {
        val filePath = System.getProperty("java.library.path")
            .split(":").asSequence()
            .map { Path(it) }
            .filter { it.exists() }
            .flatMap { it.listDirectoryEntries() }
            .firstOrNull { it.name == filename }

        if (filePath == null) throw UnsatisfiedLinkError("Failed to find library $filename")

        System.load(filePath.absolutePathString())
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    fun loadLibrary(libName: String): LibraryLoadResult {
        val filename = System.mapLibraryName(libName)
        try {
            if (composeResourcesDir != null) {
                val filePath = composeResourcesDir.resolve(filename)
                if (filePath.exists()) {
                    System.load(filePath.absolutePathString())
                    logger.info("loaded bundled native library $filename from resource directory")
                    return BundledLibrary
                }
            }

            val classPathFile = SharedLibrariesLoader::class.java.getResource("/${filename}")
            if (classPathFile != null) {
                val fileBytes = classPathFile.readBytes()
                val libFile = Files.write(tempDir.resolve(filename), fileBytes, StandardOpenOption.CREATE).toFile()
                libFile.deleteOnExit()
                System.load(libFile.path)
                logger.info("loaded bundled native library $filename from jar file")
                return BundledLibrary
            }

            logger.warn("$filename is not found in bundled libraries. attempting to load system library")
            System.loadLibrary(libName)
            return SystemLibrary
        } catch (e: UnsatisfiedLinkError) {
            logger.error("failed to load native library $filename", e)
            throw e
        }
    }

    sealed interface LibraryLoadResult {
        data object BundledLibrary : LibraryLoadResult
        data object SystemLibrary : LibraryLoadResult
    }
}
