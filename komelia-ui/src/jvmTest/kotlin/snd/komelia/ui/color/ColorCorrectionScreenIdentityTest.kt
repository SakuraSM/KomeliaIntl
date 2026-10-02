package snd.komelia.ui.color

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import snd.komelia.ui.color.view.ColorCorrectionScreen
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ColorCorrectionScreenIdentityTest {
    @Test fun editingAnotherBookCannotReuseThePreviousEditorModel() {
        assertNotEquals(ColorCorrectionScreen(KomgaBookId("A"), 1).key, ColorCorrectionScreen(KomgaBookId("B"), 1).key)
    }
    @Test fun reopeningAnEditorMustReloadSavedPolicyAndGlobalSettings() {
        assertNotEquals(ColorCorrectionScreen(KomgaBookId("A"), 1).key, ColorCorrectionScreen(KomgaBookId("A"), 1).key)
    }
    @Test fun recreationPreservesTheCurrentEditorIdentity() {
        val original = ColorCorrectionScreen(KomgaBookId("A"), 1)
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(original) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as ColorCorrectionScreen }
        assertEquals(original.key, restored.key)
    }
    @Test fun legacySerializedEditorsReceiveAStableIdentity() {
        assertEquals(-4733174237648432562L, java.io.ObjectStreamClass.lookup(ColorCorrectionScreen::class.java).serialVersionUID)
        val screen = ColorCorrectionScreen(KomgaBookId("A"), 1)
        ColorCorrectionScreen::class.java.getDeclaredField("editorId").apply { isAccessible = true }.set(screen, null)
        assertEquals(screen.key, screen.key)
    }
}
