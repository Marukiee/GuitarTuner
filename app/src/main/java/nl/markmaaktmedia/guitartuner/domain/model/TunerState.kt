package nl.markmaaktmedia.guitartuner.domain.model

import androidx.compose.runtime.Immutable
import nl.markmaaktmedia.guitartuner.audio.MicSource
import kotlin.math.abs

/** Raw output of the pitch detector for one analysis window. Emitted at ~20 Hz. */
@Immutable
data class PitchReading(
    val frequencyHz: Float,
    /** NSDF peak height, 0..1. Below ~0.85 the signal is noise or a dying note. */
    val clarity: Float,
    /** RMS of the window in dBFS; used to gate silence and to fade the UI out. */
    val levelDb: Float,
)

enum class TuningStatus { FLAT, IN_TUNE, SHARP }

/**
 * Everything the visualizer needs, already resolved against a target string.
 *
 * This is the *fast* state. It changes ~20x/second. Keep it out of [TunerUiState] so that
 * recomposition of the screen scaffold is not driven by the audio thread.
 */
@Immutable
data class TuningReading(
    val target: TuningString,
    val frequencyHz: Float,
    val cents: Float,
    val clarity: Float,
    val levelDb: Float,
) {
    val status: TuningStatus
        get() = when {
            abs(cents) <= IN_TUNE_CENTS -> TuningStatus.IN_TUNE
            cents < 0f -> TuningStatus.FLAT
            else -> TuningStatus.SHARP
        }

    /**
     * Position of the bubble on the meter: -1 = full flat, 0 = perfect, +1 = full sharp.
     * Clamped to the [-50, +50] cent range that the meter shows.
     */
    val normalizedOffset: Float
        get() = (cents / METER_RANGE_CENTS).coerceIn(-1f, 1f)

    companion object {
        /**
         * Half-width of the "perfect" window.
         *
         * Not 0.0: string inharmonicity, pick attack and finger pressure make a true zero
         * unreachable. It started at 2.5 cents, which is defensible on paper and miserable in
         * the hand, because holding a plucked string inside 2.5 cents for half a second while
         * the note decays is close to impossible. 5 cents is still at the edge of what a trained
         * ear can hear and is actually reachable.
         */
        const val IN_TUNE_CENTS = 5f

        /** Cents shown from centre to either edge of the meter. */
        const val METER_RANGE_CENTS = 50f
    }
}

/**
 * The *slow* state: user intent and configuration. Changes only on interaction, so composables
 * reading it recompose a handful of times per session.
 */
@Immutable
data class TunerUiState(
    val instrument: Instrument = Instrument.ACOUSTIC_6,
    val autoMode: Boolean = true,
    /** Physical index of the string being targeted. In auto mode this tracks detection. */
    val activeStringIndex: Int = 0,
    /** Strings the user has completed this pass; drives the peg "done" state. */
    val tunedStringIndices: Set<Int> = emptySet(),
    val referenceHz: Float = Note.STANDARD_REFERENCE_HZ,
    val micPermission: MicPermissionState = MicPermissionState.Unknown,
    val isListening: Boolean = false,
    /** Which capture path is live. Rotates automatically when one turns out to be dead. */
    val micSource: MicSource = MicSource.VoiceRecognition,
    /** True once the user has chosen a source by hand, which stops the automatic fallback. */
    val micSourcePinned: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
) {
    val activeString: TuningString
        get() = instrument.strings.getOrElse(activeStringIndex) { instrument.strings.first() }
}

enum class MicPermissionState { Unknown, Granted, Denied, PermanentlyDenied }

/** One-shot side effects. Consumed by the UI for haptics, sound and transient messages. */
sealed interface TunerEvent {
    /** A string held in tune long enough to count. Fire haptic + chime here. */
    data class StringTuned(val stringIndex: Int) : TunerEvent

    /** Auto-advance moved the target. Emitted right after [StringTuned]. */
    data class AdvancedTo(val stringIndex: Int) : TunerEvent

    /** Every string in the instrument is done. */
    data object AllStringsTuned : TunerEvent
}
