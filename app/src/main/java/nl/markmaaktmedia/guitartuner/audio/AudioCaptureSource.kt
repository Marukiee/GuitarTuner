package nl.markmaaktmedia.guitartuner.audio

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Which capture path to open.
 *
 * Not a cosmetic setting. On most phones the audio source decides *which physical
 * microphone* the recorder is wired to, and they are not interchangeable:
 *
 * - [Unprocessed] and [VoiceRecognition] usually select a secondary capsule, often the
 *   one beside the rear camera, because that is the one furthest from the earpiece.
 * - [Main] is the primary mic, beside the USB-C port at the bottom.
 * - [Camcorder] is explicitly the rear facing mic.
 *
 * [Unprocessed] is the right answer for a tuner when the hardware cooperates: it is the
 * only source Android guarantees will bypass gain control and noise suppression, and
 * both of those shift a reading by several cents and cut a decaying note short.
 */
enum class MicSource(val label: String, val androidSource: Int) {
    Unprocessed("Unprocessed", MediaRecorder.AudioSource.UNPROCESSED),
    VoiceRecognition("Voice rec.", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    Main("Main mic", MediaRecorder.AudioSource.MIC),
    Camcorder("Rear mic", MediaRecorder.AudioSource.CAMCORDER),
}

/**
 * Microphone capture as a cold [Flow] of fixed size blocks of raw samples.
 *
 * The blocks are handed on unfiltered and at the full sample rate. Band limiting,
 * decimation and windowing all live in [PitchEngine], because those depend on the
 * instrument and the instrument can change while the recorder is running: keeping them
 * here would mean tearing down and reopening `AudioRecord` every time somebody picks a
 * different tuning.
 *
 * Design notes that matter for accuracy:
 *
 * - **Voice processing off.** The effect handles are disabled by hand on our own session.
 * - **PCM float.** Reading `ENCODING_PCM_FLOAT` skips a short to float conversion pass
 *   and leaves headroom on a loud pick attack.
 */
class AudioCaptureSource(
    private val context: Context,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    /** Samples per emitted block. 2048 at 44.1 kHz is 46 ms, about 21 blocks a second. */
    val hopSize: Int = DEFAULT_HOP_SIZE,
) {

    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)

    /**
     * Sources worth trying on this device, in fallback order.
     *
     * Unprocessed leads where the device advertises it, which it did not always: on a
     * handset whose rear capsule is dead, a theoretically clean signal from a microphone
     * that does not work is worth nothing. That is what the dead microphone rotation in
     * the view model is for, and with that safety net in place the ordering can follow
     * signal quality rather than hedge against broken hardware.
     */
    fun availableSources(): List<MicSource> = buildList {
        if (audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            add(MicSource.Unprocessed)
        }
        add(MicSource.VoiceRecognition)
        add(MicSource.Main)
        add(MicSource.Camcorder)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun hops(source: MicSource): Flow<FloatArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minBuffer > 0) { "AudioRecord.getMinBufferSize failed: $minBuffer" }

        val bufferBytes = maxOf(minBuffer, hopSize * BYTES_PER_FLOAT * 4)

        val record = AudioRecord(
            source.androidSource,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferBytes,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialise for ${source.label}"
        }

        // A plugged in USB or wired mic should always win over anything built in.
        preferredInputDevice()?.let { record.preferredDevice = it }

        val effects = disableVoiceProcessing(record.audioSessionId)
        record.startRecording()

        val hop = FloatArray(hopSize)

        val reader = launch(Dispatchers.IO) {
            while (isActive) {
                var filled = 0
                while (filled < hopSize && isActive) {
                    val read = record.read(hop, filled, hopSize - filled, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        Log.w(TAG, "AudioRecord.read returned $read")
                        break
                    }
                    filled += read
                }
                if (filled < hopSize) continue
                trySend(hop.copyOf())
            }
        }

        awaitClose {
            reader.cancel()
            runCatching { record.stop() }
            record.release()
            effects.forEach { runCatching { it.release() } }
        }
    }
        // Never let a slow collector stall the reader; a stale block is worthless anyway.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .flowOn(Dispatchers.IO)

    /**
     * External inputs beat built in ones. Beyond that there is no picking a specific
     * built in mic: Android exposes all of them behind a single
     * [AudioDeviceInfo.TYPE_BUILTIN_MIC] entry, so the only lever that changes which
     * capsule is used is the audio source.
     */
    private fun preferredInputDevice(): AudioDeviceInfo? {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: return null
        return devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
    }

    /**
     * Switch off any DSP the platform attached to our session. These are no-ops on devices
     * that do not expose the effect, hence the availability checks.
     */
    private fun disableVoiceProcessing(sessionId: Int): List<AudioEffect> {
        val effects = mutableListOf<AudioEffect>()
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.also { it.enabled = false; effects += it }
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.also { it.enabled = false; effects += it }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.also { it.enabled = false; effects += it }
            }
        }.onFailure { Log.w(TAG, "Could not disable voice processing", it) }
        return effects
    }

    companion object {
        private const val TAG = "AudioCaptureSource"
        private const val BYTES_PER_FLOAT = 4

        /** 44.1 kHz is the one rate every Android device supports natively for capture. */
        const val DEFAULT_SAMPLE_RATE = 44_100

        /** 2048 samples = 46 ms between readings, about 21 a second. */
        const val DEFAULT_HOP_SIZE = 2048
    }
}
