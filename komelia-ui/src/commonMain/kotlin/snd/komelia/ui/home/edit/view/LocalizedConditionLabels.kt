package snd.komelia.ui.home.edit.view

import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.strings.LegacyStrings

fun <T : Enum<T>> T.localizedEntry(legacyStrings: LegacyStrings): LabeledEntry<T> {
    return LabeledEntry(this, legacyStrings.forText(name))
}

fun <T : Enum<T>> Iterable<T>.localizedEntries(legacyStrings: LegacyStrings): List<LabeledEntry<T>> {
    return map { it.localizedEntry(legacyStrings) }
}

fun Boolean.localizedBoolean(legacyStrings: LegacyStrings): String {
    return legacyStrings.forText(if (this) "True" else "False")
}
