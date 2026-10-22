package snd.komelia.ui.reader.epub

import snd.komelia.settings.model.EpubReaderType

/** Owns the retained implementation independently of the host chrome settings. */
internal class RetainedEpubReader {
    private var reader: EpubReaderState? = null
    private var selectedType: EpubReaderType? = null

    suspend fun select(type: EpubReaderType, create: suspend () -> EpubReaderState): EpubReaderState {
        if (selectedType == type) reader?.let { return it }
        val replacement = create()
        reader?.dispose()
        reader = replacement
        selectedType = type
        return replacement
    }

    fun dispose() {
        reader?.dispose()
        reader = null
        selectedType = null
    }
}
