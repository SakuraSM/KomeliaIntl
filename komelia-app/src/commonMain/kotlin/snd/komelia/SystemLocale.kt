package snd.komelia

/**
 * Returns the host system's locale as a BCP-47 language tag (e.g. "en-US", "zh-CN").
 * Used by the i18n layer to select an `AppStrings` instance when the user keeps
 * `AppLanguage.SYSTEM` (the default).
 */
expect fun systemLanguageTag(): String
