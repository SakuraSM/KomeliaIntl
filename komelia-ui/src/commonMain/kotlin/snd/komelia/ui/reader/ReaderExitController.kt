package snd.komelia.ui.reader

internal enum class ReaderExitAction {
    Pop,
    RestoreBookDetails,
    Ignore,
}

/** Ensures that a reader composition can trigger at most one navigation mutation. */
internal class ReaderExitController {
    private var exitConsumed = false

    fun requestExit(canPop: Boolean, hasBook: Boolean): ReaderExitAction {
        if (exitConsumed) return ReaderExitAction.Ignore

        val action = when {
            canPop -> ReaderExitAction.Pop
            hasBook -> ReaderExitAction.RestoreBookDetails
            else -> ReaderExitAction.Ignore
        }
        if (action != ReaderExitAction.Ignore) exitConsumed = true
        return action
    }
}
