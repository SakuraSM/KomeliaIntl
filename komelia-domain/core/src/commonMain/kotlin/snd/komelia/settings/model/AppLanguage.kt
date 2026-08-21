package snd.komelia.settings.model

/**
 * Application UI language.
 *
 * - [SYSTEM]: follow the host operating system locale (default).
 * - [EN]: explicit English (`en`).
 * - [ZH_CN]: explicit Simplified Chinese (`zh-CN`).
 *
 * To add a new language, add an entry here and a matching Compose Resources locale directory.
 */
enum class AppLanguage {
    SYSTEM, EN, ZH_CN
}
