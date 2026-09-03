package nl.markmaaktmedia.guitartuner.data

import android.content.Context
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
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
            ?: Instrument.ACOUSTIC
        set(value) = prefs.edit().putString(KEY_INSTRUMENT, value.name).apply()

    /**
     * The chosen tuning, stored per instrument.
     *
     * One key for all of them would mean picking Drop D on a guitar and then finding a
     * ukulele silently back on its default, or worse, on an id it does not have.
     */
    fun tuningFor(instrument: Instrument): Tuning =
        prefs.getString(KEY_TUNING_PREFIX + instrument.name, null)
            ?.let { instrument.tuningById(it) }
            ?: instrument.defaultTuning

    fun setTuning(instrument: Instrument, tuning: Tuning) {
        prefs.edit().putString(KEY_TUNING_PREFIX + instrument.name, tuning.id).apply()
    }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    var pureBlack: Boolean
        get() = prefs.getBoolean(KEY_PURE_BLACK, true)
        set(value) = prefs.edit().putBoolean(KEY_PURE_BLACK, value).apply()

    /**
     * Null until the user pins one by hand.
     *
     * Storing the automatic choice would defeat the fallback: a source that happened to
     * work once gets written down, and the rotation that would have found a better one on
     * the next launch never runs.
     */
    var micSource: MicSource?
        get() = prefs.getString(KEY_MIC_SOURCE, null)
            ?.let { name -> MicSource.entries.firstOrNull { it.name == name } }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_MIC_SOURCE).apply()
            else prefs.edit().putString(KEY_MIC_SOURCE, value.name).apply()
        }

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
        const val KEY_TUNING_PREFIX = "tuning_"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PURE_BLACK = "pure_black"
        const val KEY_MIC_SOURCE = "mic_source"
        const val KEY_THEME = "theme_mode"
        const val KEY_REFERENCE = "reference_hz"
        const val KEY_BANNER_PREVIEW = "banner_preview"
    }
}
