package snd.komelia

import snd.komelia.settings.model.AppLanguage
import snd.komelia.ui.AppLocaleController

private const val SYSTEM_LANGUAGE = "system"

object WasmAppLocaleController : AppLocaleController {
    override fun apply(language: AppLanguage) {
        val value = when (language) {
            AppLanguage.SYSTEM -> SYSTEM_LANGUAGE
            AppLanguage.EN -> "en"
            AppLanguage.ZH_CN -> "zh-CN"
        }
        persistLocaleAndReload(value)
    }
}

fun preparePersistedAppLocale() {
    applyPersistedLocaleOverride()
}

@JsFun(
    """(value) => {
        const key = 'komelia.appLanguage';
        const previous = window.localStorage.getItem(key) || 'system';
        if (previous === value) return;
        if (value === 'system') window.localStorage.removeItem(key);
        else window.localStorage.setItem(key, value);
        window.location.reload();
    }"""
)
private external fun persistLocaleAndReload(value: String)

@JsFun(
    """() => {
        const value = window.localStorage.getItem('komelia.appLanguage');
        if (!value) return;
        try {
            Object.defineProperty(window.navigator, 'language', {
                configurable: true,
                get: () => value,
            });
            Object.defineProperty(window.navigator, 'languages', {
                configurable: true,
                get: () => [value],
            });
            document.documentElement.lang = value;
        } catch (_) {
            document.documentElement.lang = value;
        }
    }"""
)
private external fun applyPersistedLocaleOverride()
