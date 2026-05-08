package snd.komelia

private fun browserLanguage(): String {
    js("return (typeof navigator !== 'undefined' && navigator.language) ? navigator.language : 'en-US';")
}

actual fun systemLanguageTag(): String = browserLanguage()
