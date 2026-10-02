package snd.komelia.db.color.jsModel

import snd.komelia.color.ColorCorrectionType
import snd.komelia.color.BookColorCorrectionMode
import snd.komelia.db.makeJsObject
import snd.komelia.db.set
import snd.komga.client.book.KomgaBookId

external interface JsBookColorCorrection : JsAny {
    val bookId: String
    val type: String
    val mode: String?
}

fun jsBookColorCorrection(bookId: KomgaBookId, type: ColorCorrectionType, mode: BookColorCorrectionMode = BookColorCorrectionMode.CUSTOM): JsBookColorCorrection {
    val jsObject = makeJsObject<JsBookColorCorrection>()
    jsObject["bookId"] = bookId.value
    jsObject["type"] = type.name
    jsObject["mode"] = mode.name
    return jsObject
}
