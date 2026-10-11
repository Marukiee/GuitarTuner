package nl.markmaaktmedia.guitartuner.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The language of the app as stored: "system" for no choice, else a language tag like "nl". */
internal fun appLanguage(): String =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-').ifBlank { "system" }

internal fun setAppLanguage(language: String) {
    AppCompatDelegate.setApplicationLocales(
        if (language == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language),
    )
}

internal val Languages = listOf("system", "en", "nl")
