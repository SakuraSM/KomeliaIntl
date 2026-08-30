package snd.komelia.offline.local

import snd.komelia.offline.media.model.MediaExtensionEpub
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.WPLink
import snd.komga.client.book.WPMetadata
import snd.komga.client.book.WPPublication
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalArchiveInspectionTest {

    @Test
    fun `local EPUB manifest resolves all relative resource links`() {
        val publication = WPPublication(
            metadata = WPMetadata(title = "Local book"),
            links = listOf(WPLink(href = "manifest.json")),
            images = listOf(WPLink(href = "images/cover.jpg")),
            readingOrder = listOf(WPLink(href = "text/chapter.xhtml")),
            resources = listOf(WPLink(href = "styles/book.css")),
            toc = listOf(
                WPLink(
                    href = "text/chapter.xhtml#start",
                    children = listOf(WPLink(href = "text/chapter-2.xhtml")),
                )
            ),
            landmarks = listOf(WPLink(href = "https://example.test/absolute.xhtml")),
        )

        val resolved = publication.withLocalBookResourceUrls(KomgaBookId("book-1"))

        assertEquals("local://device/api/v1/books/book-1/resource/text/chapter.xhtml", resolved.readingOrder.single().href)
        assertEquals("local://device/api/v1/books/book-1/resource/styles/book.css", resolved.resources.single().href)
        assertEquals("local://device/api/v1/books/book-1/resource/images/cover.jpg", resolved.images.single().href)
        assertEquals(
            "local://device/api/v1/books/book-1/resource/text/chapter-2.xhtml",
            resolved.toc.single().children.single().href,
        )
        assertEquals("https://example.test/absolute.xhtml", resolved.landmarks.single().href)
    }
    @Test
    fun stableIdsAreDeterministicAndPathSensitive() {
        assertEquals(stableId("library/series/book.cbz"), stableId("library/series/book.cbz"))
        assertNotEquals(stableId("library/series/book.cbz"), stableId("library/series/book-2.cbz"))
    }

    @Test
    fun supportedExtensionsAreCaseInsensitive() {
        assertTrue(isSupportedLocalBook("Book.CBZ"))
        assertTrue(isSupportedLocalBook("Novel.epub"))
        assertTrue(isSupportedLocalBook("Document.PDF"))
        assertFalse(isSupportedLocalBook("cover.jpg"))
    }

    @Test
    fun comicArchiveUsesNaturalPageOrderAndFirstPageAsThumbnail() {
        val bytes = mapOf(
            "page10.jpg" to byteArrayOf(10),
            "page2.jpg" to byteArrayOf(2),
            "notes.txt" to byteArrayOf(99),
        )
        val inspection = inspectComicArchive(
            entries = bytes.map { it.key to it.value.size.toLong() },
            readEntry = bytes::getValue,
            mediaType = "application/zip",
        )

        assertEquals(MediaProfile.DIVINA, inspection.mediaProfile)
        assertEquals(listOf("page2.jpg", "page10.jpg"), inspection.pages.map { it.fileName })
        assertContentEquals(byteArrayOf(2), inspection.thumbnail)
    }

    @Test
    fun epubManifestBuildsReadingOrderAndCover() {
        val files = mapOf(
            "META-INF/container.xml" to """
                <container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>
            """.trimIndent().encodeToByteArray(),
            "OPS/package.opf" to """
                <package>
                  <metadata><dc:title>Local Novel</dc:title><dc:identifier>book-id</dc:identifier><dc:language>zh</dc:language></metadata>
                  <manifest>
                    <item id="chapter-1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                  </manifest>
                  <spine><itemref idref="chapter-1"/></spine>
                </package>
            """.trimIndent().encodeToByteArray(),
            "OPS/text/chapter1.xhtml" to "chapter".encodeToByteArray(),
            "OPS/images/cover.jpg" to byteArrayOf(1, 2, 3),
        )

        val inspection = inspectEpubArchive(files.keys.toList(), files::getValue)
        val extension = inspection.extension as MediaExtensionEpub

        assertEquals(MediaProfile.EPUB, inspection.mediaProfile)
        assertEquals("Local Novel", extension.manifest.metadata.title)
        assertEquals("OPS/text/chapter1.xhtml", extension.manifest.readingOrder.single().href)
        assertContentEquals(byteArrayOf(1, 2, 3), inspection.thumbnail)
    }
}
