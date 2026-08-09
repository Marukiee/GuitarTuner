package nl.markmaaktmedia.guitartuner.data

import android.content.Context
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode

/**
 * The handful of choices that must survive a restart.
 *
 * SharedPreferences rather than DataStore on purpose: every one of these is read exactly once, at
 * startup, before the first frame. DataStore's suspending read would mean either blocking the
 * first composition or rendering the wrong theme for a frame and then flipping, and there is
 * nothing here that justifies that.
 */
class TunerPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var instrument: Instrument
        get() = prefs.getString(KEY_INSTRUMENT, null)
            ?.let { name -> Instrument.entries.firstOrNull { it.name == name } }
            ?: Instrument.ACOUSTIC_6
        set(value) = prefs.edit().putString(KEY_INSTRUMENT, value.name).apply()

    /**
     * Defaults to [MicSource.VoiceRecognition].
     *
     * UNPROCESSED is the theoretically better choice, since it is the only source guaranteed to
     * bypass the platform's gain and noise processing. In practice it selects a secondary capsule
     * on most handsets, which is a coin flip on whether you get a usable signal at all. Voice
     * recognition has AGC off on nearly every OEM and picks a mic that actually works.
     */
    var micSource: MicSource
        get() = prefs.getString(KEY_MIC_SOURCE, null)
            ?.let { name -> MicSource.entries.firstOrNull { it.name == name } }
            ?: MicSource.VoiceRecognition
        set(value) = prefs.edit().putString(KEY_MIC_SOURCE, value.name).apply()

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Developer option: render the update banner with dummy content so it can be checked. */
    var bannerPreview: Boolean
        get() = prefs.getBoolean(KEY_BANNER_PREVIEW, false)
        set(value) = prefs.edit().putBoolean(KEY_BANNER_PREVIEW, value).apply()

    var referenceHz: Float
        get() = prefs.getFloat(KEY_REFERENCE, Note.STANDARD_REFERENCE_HZ)
        set(value) = prefs.edit().putFloat(KEY_REFERENCE, value).apply()

    private companion object {
        const val NAME = "guitartuner.settings"
        const val KEY_INSTRUMENT = "instrument"
        const val KEY_MIC_SOURCE = "mic_source"
        const val KEY_THEME = "theme_mode"
        const val KEY_REFERENCE = "reference_hz"
        const val KEY_BANNER_PREVIEW = "banner_preview"
    }
}
