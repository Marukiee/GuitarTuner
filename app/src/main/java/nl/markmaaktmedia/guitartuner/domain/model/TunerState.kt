package nl.markmaaktmedia.guitartuner.domain.model

import androidx.compose.runtime.Immutable
import nl.markmaaktmedia.guitartuner.audio.MicSource
import kotlin.math.abs

/** Raw output of the pitch detector for one analysis window. Emitted at ~21 Hz. */
@Immutable
data class PitchReading(
    val frequencyHz: Float,
    /** NSDF peak height, 0..1. Below ~0.85 the window is noise or a dying note. */
    val clarity: Float,
    /** RMS of the window in dBFS; gates silence and fades the UI out. */
    val levelDb: Float,
    /**
     * True once several consecutive estimates agree to within a few cents.
     *
     * A plucked string is not stable for the first couple of hundred milliseconds, and a
     * tuner that commits to the attack transient shows a number that is confidently
     * wrong. Everything that matters (the readout, the hold clock, auto advance) waits
     * for this.
     */
    val settled: Boolean,
)

enum class TuningStatus { FLAT, IN_TUNE, SHARP }

/**
 * Everything the dial needs, already resolved against a target string.
 *
 * This is the *fast* state. It changes ~21x a second, so it is kept out of
 * [TunerUiState]: recomposition of the screen scaffold must not be driven by the audio
 * thread.
 */
@Immutable
data class TuningReading(
    val target: TuningString,
    val frequencyHz: Float,
    val cents: Float,
    val clarity: Float,
    val levelDb: Float,
    val settled: Boolean,
) {
    val status: TuningStatus
        get() = when {
            abs(cents) <= IN_TUNE_CENTS -> TuningStatus.IN_TUNE
            cents < 0f -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }

    /**
     * Position on the meter: -1 = full flat, 0 = perfect, +1 = full sharp, clamped to the
     * range the dial actually shows.
     */
    val normalizedOffset: Float
        get() = (cents / METER_RANGE_CENTS).coerceIn(-1f, 1f)

    companion object {
        /**
         * Half width of the "perfect" window.
         *
         * Not zero: inharmonicity, pick attack and finger pressure make a true zero
         * unreachable. It started at 2.5 cents, which is defensible on paper and
         * miserable in the hand, because holding a plucked string inside 2.5 cents for
         * half a second while it decays is close to impossible. 5 cents is still at the
         * edge of what a trained ear hears, and it is reachable.
         */
        const val IN_TUNE_CENTS = 5f

        /** Cents from the centre to either edge of the dial. */
        const val METER_RANGE_CENTS = 50f
    }
}

/**
 * The *slow* state: user intent and configuration. Changes only on interaction, so
 * composables reading it recompose a handful of times per session.
 */
@Immutable
data class TunerUiState(
    val instrument: Instrument = Instrument.ACOUSTIC,
    val tuning: Tuning = Instrument.ACOUSTIC.defaultTuning,
    val autoMode: Boolean = true,
    /** Physical index of the string being targeted. In auto mode this tracks detection. */
    val activeStringIndex: Int = 0,
    /** Strings completed this pass; drives the peg and chip "done" state. */
    val tunedStringIndices: Set<Int> = emptySet(),
    val referenceHz: Float = Note.STANDARD_REFERENCE_HZ,
    val micPermission: MicPermissionState = MicPermissionState.Unknown,
    val isListening: Boolean = false,
    /** Suppressed while the reference tone is sounding, so the app cannot hear itself. */
    val isMuted: Boolean = false,
    /** Physical index of the string currently sounding, or null. */
    val soundingStringIndex: Int? = null,
    /** Which capture path is live. Rotates automatically when one turns out to be dead. */
    val micSource: MicSource = MicSource.VoiceRecognition,
    /** True once the user has chosen a source by hand, which stops the automatic fallback. */
    val micSourcePinned: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = true,
    /** Developer option: force the update banner on so it can be inspected. */
    val bannerPreview: Boolean = false,
) {
    val strings: List<TuningString> get() = tuning.strings

    val activeString: TuningString
        get() = tuning.strings.getOrElse(activeStringIndex) { tuning.strings.first() }

    val allTuned: Boolean get() = tunedStringIndices.size >= tuning.stringCount
}

enum class MicPermissionState { Unknown, Granted, Denied, PermanentlyDenied }

/** One-shot side effects. Consumed by the UI for haptics, sound and transient messages. */
sealed interface TunerEvent {
    /** A string held in tune long enough to count. Fire haptic and chime here. */
    data class StringTuned(val stringIndex: Int) : TunerEvent

    /** Auto advance moved the target. Emitted right after [StringTuned]. */
    data class AdvancedTo(val stringIndex: Int) : TunerEvent

    /** Every string in the tuning is done. */
    data object AllStringsTuned : TunerEvent
}
