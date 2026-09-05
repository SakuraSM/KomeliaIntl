package snd.komelia.ui.reader.image.common

internal enum class ReaderBackAction { ShowControls, HideControls, Exit }

/** Mobile PDF readers expose controls on the first Back and exit on the next. */
internal fun readerBackAction(confirmExit: Boolean, controlsVisible: Boolean): ReaderBackAction =
    when {
        confirmExit && !controlsVisible -> ReaderBackAction.ShowControls
        confirmExit -> ReaderBackAction.Exit
        controlsVisible -> ReaderBackAction.HideControls
        else -> ReaderBackAction.Exit
    }
