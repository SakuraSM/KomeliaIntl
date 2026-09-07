package snd.komelia.offline.local

import snd.komelia.offline.media.model.MediaExtensionEpub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalEpubNavigationTest {
    @Test
    fun `EPUB3 imports nested navigation landmarks and page list`() {
        val extension = inspectNavigation("nav.xhtml", "application/xhtml+xml", "properties='nav'", """
            <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
              <nav epub:type="toc"><ol><li><span>Part &amp; One</span><ol>
                <li><a href="../text/first.xhtml#begin">First <em>chapter</em></a></li>
              </ol></li></ol></nav>
              <nav epub:type="landmarks"><ol><li><a href="../text/first.xhtml">Start</a></li></ol></nav>
              <nav epub:type="page-list"><ol><li><a href="../text/first.xhtml#p1">1</a></li></ol></nav>
            </body></html>
        """)
        assertEquals("Part & One", extension.toc.single().title)
        assertEquals("OPS/text/first.xhtml#begin", extension.toc.single().children.single().href)
        assertEquals("First chapter", extension.manifest.toc.single().children.single().title)
        assertEquals(1, extension.landmarks.size)
        assertEquals(1, extension.manifest.pageList.size)
    }

    @Test
    fun `EPUB2 imports nested NCX relative to navigation document`() {
        val extension = inspectNavigation("toc.ncx", "application/x-dtbncx+xml", "", """
            <ncx><navMap><navPoint><navLabel><text>First</text></navLabel>
              <content src="../text/first.xhtml"/>
              <navPoint><navLabel><text>Second</text></navLabel><content src="../text/second.xhtml#section"/></navPoint>
            </navPoint></navMap></ncx>
        """)
        assertEquals("First", extension.toc.single().title)
        assertEquals("OPS/text/second.xhtml#section", extension.toc.single().children.single().href)
    }

    @Test
    fun `local positions cover each resource including both endpoints without inventing pages`() {
        val extension = inspectNavigation("nav.xhtml", "application/xhtml+xml", "properties='nav'", "<html/>")
        assertTrue(extension.positions.isNotEmpty())
        assertEquals(setOf("OPS/text/first.xhtml", "OPS/text/second.xhtml"), extension.positions.map { it.href }.toSet())
        assertEquals(0f, extension.positions.first().locations?.totalProgression)
        assertEquals(1f, extension.positions.last().locations?.totalProgression)
        extension.positions.groupBy { it.href }.values.forEach { positions ->
            assertEquals(0f, positions.first().locations?.progression)
            assertEquals(1f, positions.last().locations?.progression)
        }
        assertTrue(extension.positions.zipWithNext().all { (before, after) ->
            before.locations!!.totalProgression!! <= after.locations!!.totalProgression!!
        })
        assertEquals(2, extension.manifest.toc.size, "Missing TOC should fall back to the reading order")
    }

    private fun inspectNavigation(name: String, type: String, properties: String, navigation: String): MediaExtensionEpub {
        val files = mapOf(
            "META-INF/container.xml" to "<container><rootfile full-path='OPS/book.opf'/></container>",
            "OPS/book.opf" to """
                <package><metadata><dc:title>Fixture</dc:title></metadata><manifest>
                  <item id="first" href="text/first.xhtml" media-type="application/xhtml+xml"/>
                  <item id="second" href="text/second.xhtml" media-type="application/xhtml+xml"/>
                  <item id="navigation" href="nav/$name" media-type="$type" $properties/>
                </manifest><spine toc="navigation"><itemref idref="first"/><itemref idref="second"/></spine></package>
            """,
            "OPS/nav/$name" to navigation,
            "OPS/text/first.xhtml" to "<html><body><h1>First</h1><p>${"Text ".repeat(600)}</p></body></html>",
            "OPS/text/second.xhtml" to "<html><body><h1>Second</h1></body></html>",
        ).mapValues { it.value.encodeToByteArray() }
        val inspection = inspectEpubArchive(files.keys.toList(), files::getValue)
        assertTrue(inspection.pages.isEmpty())
        return inspection.extension as MediaExtensionEpub
    }
}
