package nl.markmaaktmedia.guitartuner.ui

import android.Manifest
import android.app.Application
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.audio.AudioCaptureSource
import nl.markmaaktmedia.guitartuner.audio.McLeodPitchDetector
import nl.markmaaktmedia.guitartuner.audio.PitchEngine
import nl.markmaaktmedia.guitartuner.domain.InTuneTracker
import nl.markmaaktmedia.guitartuner.domain.StringMatcher
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.MicPermissionState
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.TunerEvent
import nl.markmaaktmedia.guitartuner.domain.model.TunerUiState
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading

/**
 * The state is deliberately split in two.
 *
 * [uiState] changes only when the user does something: pick an instrument, flip auto mode, tap a
 * peg. Composables reading it recompose a handful of times per session.
 *
 * [reading] changes about 21 times a second. Anything reading it recomposes 21 times a second,
 * which is why the visualizer must consume it through an `Animatable` inside a `graphicsLayer`
 * or `Canvas` lambda: that keeps the audio rate confined to the draw phase and never triggers
 * recomposition or relayout at all.
 *
 * [events] carries the one shot stuff (haptics, chime) that must fire exactly once.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val capture = AudioCaptureSource(application)
    private val detector = McLeodPitchDetector(sampleRate = capture.sampleRate)
    private val engine = PitchEngine(capture, detector)

    private val matcher = StringMatcher()
    private val inTuneTracker = InTuneTracker()

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private val _reading = MutableStateFlow<TuningReading?>(null)
    val reading: StateFlow<TuningReading?> = _reading.asStateFlow()

    private val _events = MutableSharedFlow<TunerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TunerEvent> = _events.asSharedFlow()

    private var listenJob: Job? = null

    init {
        engine.configureFor(_uiState.value.instrument)
    }

    // region user intent

    fun selectInstrument(instrument: Instrument) {
        if (instrument == _uiState.value.instrument) return
        engine.configureFor(instrument)
        inTuneTracker.reset()
        _reading.value = null
        _uiState.update {
            it.copy(
                instrument = instrument,
                activeStringIndex = instrument.ascendingByPitch.first().physicalIndex,
                tunedStringIndices = emptySet(),
            )
        }
    }

    fun setAutoMode(enabled: Boolean) {
        inTuneTracker.reset()
        _uiState.update { it.copy(autoMode = enabled) }
    }

    /**
     * Tapping a peg locks the tuner to that string and drops out of auto mode, per the spec:
     * from here on every other frequency is measured against this one target.
     */
    fun selectString(physicalIndex: Int) {
        inTuneTracker.reset()
        _uiState.update {
            if (physicalIndex !in it.instrument.strings.indices) it
            else it.copy(activeStringIndex = physicalIndex, autoMode = false)
        }
    }

    fun setReferencePitch(hz: Float) {
        _uiState.update { it.copy(referenceHz = hz.coerceIn(400f, 480f)) }
    }

    fun onPermissionResult(state: MicPermissionState) {
        _uiState.update { it.copy(micPermission = state) }
    }

    fun resetProgress() {
        inTuneTracker.reset()
        _uiState.update {
            it.copy(
                tunedStringIndices = emptySet(),
                activeStringIndex = it.instrument.ascendingByPitch.first().physicalIndex,
            )
        }
    }

    // endregion

    // region listening lifecycle

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (listenJob?.isActive == true) return
        _uiState.update { it.copy(isListening = true) }
        listenJob = viewModelScope.launch {
            engine.readings().collect { pitch ->
                if (pitch == null) {
                    _reading.value = null
                    inTuneTracker.update(null, SystemClock.elapsedRealtime())
                    return@collect
                }
                handlePitch(pitch.frequencyHz, pitch.clarity, pitch.levelDb)
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        inTuneTracker.reset()
        _reading.value = null
        _uiState.update { it.copy(isListening = false) }
    }

    // endregion

    private fun handlePitch(frequencyHz: Float, clarity: Float, levelDb: Float) {
        val state = _uiState.value

        val targetIndex = if (state.autoMode) {
            matcher.match(
                frequencyHz = frequencyHz,
                instrument = state.instrument,
                referenceHz = state.referenceHz,
                currentIndex = state.activeStringIndex,
            ) ?: state.activeStringIndex
        } else {
            state.activeStringIndex
        }

        if (targetIndex != state.activeStringIndex) {
            inTuneTracker.reset()
            _uiState.update { it.copy(activeStringIndex = targetIndex) }
        }

        val target = state.instrument.strings[targetIndex]
        // Fold octave errors onto the target so a detected 2nd harmonic reads as "in tune"
        // rather than "1200 cents sharp".
        val cents = if (state.autoMode) {
            matcher.foldedCents(frequencyHz, target, state.referenceHz)
        } else {
            Note.centsBetween(frequencyHz, target.targetHz(state.referenceHz))
        }

        val newReading = TuningReading(
            target = target,
            frequencyHz = frequencyHz,
            cents = cents,
            clarity = clarity,
            levelDb = levelDb,
        )
        _reading.value = newReading

        if (inTuneTracker.update(newReading, SystemClock.elapsedRealtime())) {
            onStringTuned(targetIndex)
        }
    }

    /**
     * Held in tune for the full hold window. Announce it, then in auto mode move the target to
     * the next string by *pitch*, not by peg position, so a re-entrant ukulele advances
     * C4 -> E4 -> G4 -> A4 rather than following the headstock.
     */
    private fun onStringTuned(physicalIndex: Int) {
        val state = _uiState.value
        val tuned = state.tunedStringIndices + physicalIndex
        _events.tryEmit(TunerEvent.StringTuned(physicalIndex))

        if (!state.autoMode) {
            _uiState.update { it.copy(tunedStringIndices = tuned) }
            return
        }

        val sequence = state.instrument.ascendingByPitch
        val position = sequence.indexOfFirst { it.physicalIndex == physicalIndex }
        val next = sequence.drop(position + 1).firstOrNull { it.physicalIndex !in tuned }
            ?: sequence.firstOrNull { it.physicalIndex !in tuned }

        if (next == null) {
            _uiState.update { it.copy(tunedStringIndices = tuned) }
            _events.tryEmit(TunerEvent.AllStringsTuned)
            return
        }

        inTuneTracker.reset()
        _uiState.update { it.copy(tunedStringIndices = tuned, activeStringIndex = next.physicalIndex) }
        _events.tryEmit(TunerEvent.AdvancedTo(next.physicalIndex))
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
