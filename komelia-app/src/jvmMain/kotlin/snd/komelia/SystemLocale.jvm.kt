package snd.komelia

import java.util.Locale

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()
