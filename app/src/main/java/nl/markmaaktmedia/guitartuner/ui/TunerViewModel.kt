package nl.markmaaktmedia.guitartuner.ui

import android.Manifest
import android.app.Application
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.guitartuner.audio.AudioCaptureSource
import nl.markmaaktmedia.guitartuner.audio.MicSource
import nl.markmaaktmedia.guitartuner.audio.PitchEngine
import nl.markmaaktmedia.guitartuner.audio.ReferenceTonePlayer
import nl.markmaaktmedia.guitartuner.data.TunerPreferences
import nl.markmaaktmedia.guitartuner.domain.InTuneTracker
import nl.markmaaktmedia.guitartuner.domain.StringMatcher
import nl.markmaaktmedia.guitartuner.domain.model.Instrument
import nl.markmaaktmedia.guitartuner.domain.model.MicPermissionState
import nl.markmaaktmedia.guitartuner.domain.model.Note
import nl.markmaaktmedia.guitartuner.domain.model.ThemeMode
import nl.markmaaktmedia.guitartuner.domain.model.TunerEvent
import nl.markmaaktmedia.guitartuner.domain.model.TunerUiState
import nl.markmaaktmedia.guitartuner.domain.model.Tuning
import nl.markmaaktmedia.guitartuner.domain.model.TuningReading
import nl.markmaaktmedia.guitartuner.domain.model.TuningStatus

/**
 * The state is deliberately split.
 *
 * [uiState] changes only when the user does something: pick an instrument or a tuning,
 * flip auto mode, tap a string. Composables reading it recompose a handful of times per
 * session.
 *
 * [reading], [inputLevelDb] and [holdProgress] change about 21 times a second. Anything
 * reading them recomposes 21 times a second, which is why the dial consumes them through
 * an `Animatable` inside a `Canvas` or `graphicsLayer` lambda: that keeps the audio rate
 * confined to the draw phase.
 *
 * [events] carries the one shot things (haptics, chime) that must fire exactly once.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val capture = AudioCaptureSource(application)
    private val engine = PitchEngine(capture)
    private val tonePlayer = ReferenceTonePlayer()

    private val matcher = StringMatcher()
    private val inTuneTracker = InTuneTracker()

    private val sourceCandidates = engine.availableSources()
    private val preferences = TunerPreferences(application)

    private val _uiState: MutableStateFlow<TunerUiState>
    val uiState: StateFlow<TunerUiState>

    init {
        val instrument = preferences.instrument
        val tuning = preferences.tuningFor(instrument)
        val storedSource = preferences.micSource
        _uiState = MutableStateFlow(
            TunerUiState(
                instrument = instrument,
                tuning = tuning,
                activeStringIndex = tuning.ascendingByPitch.first().physicalIndex,
                referenceHz = preferences.referenceHz,
                micSource = storedSource ?: sourceCandidates.first(),
                // A stored source is a deliberate choice, so the fallback stays out of it.
                micSourcePinned = storedSource != null,
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColor,
                pureBlack = preferences.pureBlack,
                bannerPreview = preferences.bannerPreview,
            ),
        )
        uiState = _uiState.asStateFlow()
        engine.configureFor(tuning)
    }

    private val _reading = MutableStateFlow<TuningReading?>(null)
    val reading: StateFlow<TuningReading?> = _reading.asStateFlow()

    /** Raw input level in dBFS, so the UI can prove that audio is arriving at all. */
    private val _inputLevelDb = MutableStateFlow(SILENCE_DB)
    val inputLevelDb: StateFlow<Float> = _inputLevelDb.asStateFlow()

    /** 0..1 through the "held in tune" window, for the ring that fills before the chime. */
    private val _holdProgress = MutableStateFlow(0f)
    val holdProgress: StateFlow<Float> = _holdProgress.asStateFlow()

    private val _events = MutableSharedFlow<TunerEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TunerEvent> = _events.asSharedFlow()

    /**
     * Is the string being listened to in tune *right now*?
     *
     * Separate from [reading] and deliberately a Boolean. The string rail needs live
     * feedback the moment the note is hit, not half a second later when the hold
     * completes, but it must not recompose 21 times a second to get it. Collapsing to a
     * Boolean through `distinctUntilChanged` means it recomposes a couple of times per
     * string instead of a couple of hundred.
     */
    val inTuneNow: StateFlow<Boolean> = _reading
        .map { it != null && it.settled && it.status == TuningStatus.IN_TUNE && it.clarity >= 0.8f }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var listenJob: Job? = null
    private var toneJob: Job? = null

    /** Frames in a row that carried effectively nothing. Drives the dead microphone fallback. */
    private var silentFrames = 0

    /** Once a source has actually delivered signal we stop second-guessing it. */
    private var sourceProven = false

    // region user intent

    fun selectInstrument(instrument: Instrument) {
        if (instrument == _uiState.value.instrument) return
        val tuning = preferences.tuningFor(instrument)
        preferences.instrument = instrument
        applyTuning(instrument, tuning)
    }

    fun selectTuning(tuning: Tuning) {
        val state = _uiState.value
        if (tuning.id == state.tuning.id) return
        preferences.setTuning(state.instrument, tuning)
        applyTuning(state.instrument, tuning)
    }

    private fun applyTuning(instrument: Instrument, tuning: Tuning) {
        engine.configureFor(tuning)
        inTuneTracker.reset()
        _reading.value = null
        _holdProgress.value = 0f
        _uiState.update {
            it.copy(
                instrument = instrument,
                tuning = tuning,
                activeStringIndex = tuning.ascendingByPitch.first().physicalIndex,
                tunedStringIndices = emptySet(),
            )
        }
    }

    fun setAutoMode(enabled: Boolean) {
        inTuneTracker.reset()
        _uiState.update { it.copy(autoMode = enabled) }
    }

    /**
     * Tapping a string locks the tuner to it and drops out of auto mode: from here on
     * every frequency is measured against this one target, however far away it lands.
     */
    fun selectString(physicalIndex: Int) {
        inTuneTracker.reset()
        _holdProgress.value = 0f
        _uiState.update {
            if (physicalIndex !in it.tuning.strings.indices) it
            else it.copy(activeStringIndex = physicalIndex, autoMode = false)
        }
    }

    /**
     * Sounds the target note through the speaker.
     *
     * Listening is suspended for as long as the tone lasts plus a moment for the room to
     * go quiet. Without that the app hears its own reference tone, locks onto it, and
     * reports the string as perfectly in tune while nobody is playing.
     */
    fun playReferenceTone(physicalIndex: Int) {
        val state = _uiState.value
        val string = state.tuning.strings.getOrNull(physicalIndex) ?: return
        toneJob?.cancel()
        toneJob = viewModelScope.launch {
            val duration = tonePlayer.play(string.targetHz(state.referenceHz))
            if (duration <= 0) return@launch
            inTuneTracker.reset()
            _reading.value = null
            _holdProgress.value = 0f
            _uiState.update { it.copy(isMuted = true, soundingStringIndex = physicalIndex) }
            delay(duration.toLong() + MUTE_TAIL_MS)
            _uiState.update { it.copy(isMuted = false, soundingStringIndex = null) }
        }
    }

    fun stopReferenceTone() {
        toneJob?.cancel()
        tonePlayer.stop()
        _uiState.update { it.copy(isMuted = false, soundingStringIndex = null) }
    }

    fun setReferencePitch(hz: Float) {
        val clamped = hz.coerceIn(400f, 480f)
        preferences.referenceHz = clamped
        _uiState.update { it.copy(referenceHz = clamped) }
    }

    fun setBannerPreview(enabled: Boolean) {
        preferences.bannerPreview = enabled
        _uiState.update { it.copy(bannerPreview = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        preferences.dynamicColor = enabled
        _uiState.update { it.copy(dynamicColor = enabled) }
    }

    fun setPureBlack(enabled: Boolean) {
        preferences.pureBlack = enabled
        _uiState.update { it.copy(pureBlack = enabled) }
    }

    /** Explicit choice from Settings. Pins the source so the fallback leaves it alone. */
    fun setMicSource(source: MicSource) {
        if (source == _uiState.value.micSource && _uiState.value.micSourcePinned) return
        preferences.micSource = source
        sourceProven = true
        _uiState.update { it.copy(micSource = source, micSourcePinned = true) }
        restartListening()
    }

    fun micSourceOptions(): List<MicSource> = sourceCandidates

    fun onPermissionResult(state: MicPermissionState) {
        _uiState.update { it.copy(micPermission = state) }
    }

    fun resetProgress() {
        inTuneTracker.reset()
        _holdProgress.value = 0f
        _uiState.update {
            it.copy(
                tunedStringIndices = emptySet(),
                activeStringIndex = it.tuning.ascendingByPitch.first().physicalIndex,
            )
        }
    }

    // endregion

    // region listening lifecycle

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (listenJob?.isActive == true) return
        silentFrames = 0
        _uiState.update { it.copy(isListening = true) }
        listenJob = viewModelScope.launch {
            engine.frames(_uiState.value.micSource).collect { frame ->
                _inputLevelDb.value = frame.levelDb
                if (_uiState.value.isMuted) return@collect
                watchForDeadMicrophone(frame.levelDb)

                val now = SystemClock.elapsedRealtime()
                val pitch = frame.pitch
                if (pitch == null) {
                    _reading.value = null
                    inTuneTracker.update(null, now)
                    _holdProgress.value = inTuneTracker.holdProgress(now)
                    return@collect
                }
                handlePitch(pitch.frequencyHz, pitch.clarity, pitch.levelDb, pitch.settled, now)
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        stopReferenceTone()
        inTuneTracker.reset()
        _reading.value = null
        _holdProgress.value = 0f
        _inputLevelDb.value = SILENCE_DB
        _uiState.update { it.copy(isListening = false) }
    }

    @Suppress("MissingPermission")
    private fun restartListening() {
        if (_uiState.value.micPermission != MicPermissionState.Granted) return
        stopListening()
        startListening()
    }

    /**
     * Rotates to the next capture path when the current one delivers nothing at all.
     *
     * A handset can have a physically broken microphone, and the sources this app prefers
     * for accuracy are exactly the ones that tend to select a secondary capsule. Without
     * this the app looks merely unresponsive, which is a miserable thing to debug from the
     * outside.
     *
     * The threshold sits well below room tone, so a quiet room does not trigger it. Once
     * any frame clears it the source counts as proven and rotation stops for the session.
     */
    private fun watchForDeadMicrophone(levelDb: Float) {
        if (sourceProven || _uiState.value.micSourcePinned) return

        if (levelDb > DEAD_MIC_DB) {
            sourceProven = true
            silentFrames = 0
            return
        }

        silentFrames++
        if (silentFrames < DEAD_MIC_FRAMES) return

        val current = sourceCandidates.indexOf(_uiState.value.micSource)
        if (current == sourceCandidates.lastIndex) {
            // Everything has been tried. Stop cycling; the input meter shows a flat line
            // and the user can take it from there.
            sourceProven = true
            return
        }
        _uiState.update { it.copy(micSource = sourceCandidates[current + 1]) }
        restartListening()
    }

    // endregion

    private fun handlePitch(
        frequencyHz: Float,
        clarity: Float,
        levelDb: Float,
        settled: Boolean,
        now: Long,
    ) {
        val state = _uiState.value

        // Only a settled reading is allowed to move the target. During the attack the
        // estimate sweeps, and a matcher fed sweeping estimates hops between strings.
        val targetIndex = if (state.autoMode && settled) {
            matcher.match(
                frequencyHz = frequencyHz,
                tuning = state.tuning,
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

        val target = state.tuning.strings[targetIndex]
        // Fold a surviving octave error onto the target so it reads as "in tune" rather
        // than "1200 cents sharp". The engine's anchor catches nearly all of them first.
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
            settled = settled,
        )
        _reading.value = newReading

        val fired = inTuneTracker.update(newReading, now)
        _holdProgress.value = inTuneTracker.holdProgress(now)
        if (fired) onStringTuned(targetIndex)
    }

    /**
     * Held in tune for the full window. Announce it, then in auto mode move the target to
     * the next string by *pitch*, not by peg position, so a re-entrant ukulele advances
     * C4, E4, G4, A4 rather than following the headstock.
     */
    private fun onStringTuned(physicalIndex: Int) {
        val state = _uiState.value
        val tuned = state.tunedStringIndices + physicalIndex
        _events.tryEmit(TunerEvent.StringTuned(physicalIndex))

        if (!state.autoMode) {
            _uiState.update { it.copy(tunedStringIndices = tuned) }
            return
        }

        val sequence = state.tuning.ascendingByPitch
        val position = sequence.indexOfFirst { it.physicalIndex == physicalIndex }
        val next = sequence.drop(position + 1).firstOrNull { it.physicalIndex !in tuned }
            ?: sequence.firstOrNull { it.physicalIndex !in tuned }

        if (next == null) {
            _uiState.update { it.copy(tunedStringIndices = tuned) }
            _events.tryEmit(TunerEvent.AllStringsTuned)
            return
        }

        inTuneTracker.reset()
        _holdProgress.value = 0f
        _uiState.update { it.copy(tunedStringIndices = tuned, activeStringIndex = next.physicalIndex) }
        _events.tryEmit(TunerEvent.AdvancedTo(next.physicalIndex))
    }

    override fun onCleared() {
        stopListening()
        tonePlayer.release()
        super.onCleared()
    }

    private companion object {
        const val SILENCE_DB = -120f

        /** Below this there is genuinely nothing on the wire; room tone sits well above it. */
        const val DEAD_MIC_DB = -75f

        /** ~2 seconds at a 46 ms hop before giving up on a capture path. */
        const val DEAD_MIC_FRAMES = 44

        /** Time after the reference tone before listening resumes, for the room to settle. */
        const val MUTE_TAIL_MS = 250L
    }
}
