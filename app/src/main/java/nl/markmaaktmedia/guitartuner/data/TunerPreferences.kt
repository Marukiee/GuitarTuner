package nl.markmaaktmedia.guitartuner.data

import android.content.Context
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.ui.theme.Appearance
import nl.markmaaktmedia.guitartuner.ui.theme.ColourSeed
import nl.markmaaktmedia.guitartuner.ui.theme.PaletteStyle

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

    var pureBlack: Boolean
        // Off by default: a true black panel is a choice, not a starting point.
        get() = prefs.getBoolean(KEY_PURE_BLACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PURE_BLACK, value).apply()

    var seed: ColourSeed
        get() = prefs.getString(KEY_SEED, null)?.let(ColourSeed::fromKey)
            // The old switch becomes the new first choice: wallpaper on stays wallpaper, off
            // lands on the indigo the app used to fall back to.
            ?: if (prefs.getBoolean(KEY_LEGACY_DYNAMIC_COLOR, true)) ColourSeed.WALLPAPER else ColourSeed.INDIGO
        set(value) = prefs.edit().putString(KEY_SEED, value.storageKey).apply()

    var style: PaletteStyle
        get() = PaletteStyle.fromKey(prefs.getString(KEY_STYLE, null))
        set(value) = prefs.edit().putString(KEY_STYLE, value.storageKey).apply()

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** The look as one value, read once before the first frame so the app opens in it. */
    var appearance: Appearance
        get() = Appearance(themeMode, seed, style, pureBlack)
        set(value) {
            themeMode = value.mode
            seed = value.seed
            style = value.style
            pureBlack = value.pureBlack
        }

    /** A phone that dims while you tune is a phone you have to touch to keep tuning. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var chime: Boolean
        get() = prefs.getBoolean(KEY_CHIME, true)
        set(value) = prefs.edit().putBoolean(KEY_CHIME, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

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
        const val KEY_LEGACY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_SEED = "colour_seed"
        const val KEY_STYLE = "palette_style"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_CHIME = "chime"
        const val KEY_HAPTICS = "haptics"
        const val KEY_PURE_BLACK = "pure_black"
        const val KEY_MIC_SOURCE = "mic_source"
        const val KEY_THEME = "theme_mode"
        const val KEY_REFERENCE = "reference_hz"
        const val KEY_BANNER_PREVIEW = "banner_preview"
    }
}
