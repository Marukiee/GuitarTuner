package nl.markmaaktmedia.guitartuner.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
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
 * Microphone capture as a cold [Flow] of overlapping analysis windows.
 *
 * Design notes that matter for tuning accuracy:
 *
 * - **Audio source.** `MIC` routes through the platform voice pipeline on most devices, which
 *   applies AGC and noise suppression. Both mangle the waveform enough to shift the detected
 *   pitch by several cents and to kill a decaying note early. We prefer `UNPROCESSED` when the
 *   device advertises it, fall back to `VOICE_RECOGNITION` (AGC off on nearly all OEMs) and only
 *   then to `MIC`. The effect handles are additionally disabled by hand below.
 * - **PCM float.** Reading `ENCODING_PCM_FLOAT` avoids a short-to-float conversion pass and gives
 *   headroom on loud pick attacks.
 * - **Overlap.** Windows are [windowSize] long but advance by [hopSize], so the UI updates every
 *   `hopSize / sampleRate` seconds (46 ms at the defaults) while each analysis still sees enough
 *   periods of a low E to be reliable.
 */
class AudioCaptureSource(
    private val context: Context,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val hopSize: Int = DEFAULT_HOP_SIZE,
) {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun windows(): Flow<FloatArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minBuffer > 0) { "AudioRecord.getMinBufferSize failed: $minBuffer" }

        // Four hops of slack so a scheduling hiccup does not drop samples.
        val bufferBytes = maxOf(minBuffer, windowSize * BYTES_PER_FLOAT * 2)

        val record = AudioRecord(
            preferredAudioSource(),
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferBytes,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialise"
        }

        val effects = disableVoiceProcessing(record.audioSessionId)
        record.startRecording()

        val window = FloatArray(windowSize)
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

                // Slide the window left by one hop, then append the new samples.
                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                System.arraycopy(hop, 0, window, windowSize - hopSize, hopSize)

                trySend(window.copyOf())
            }
        }

        awaitClose {
            reader.cancel()
            runCatching { record.stop() }
            record.release()
            effects.forEach { runCatching { it.release() } }
        }
    }
        // Never let a slow collector stall the reader; a stale window is worthless anyway.
        .buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .flowOn(Dispatchers.IO)

    private fun preferredAudioSource(): Int {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessedSupported =
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return when {
            unprocessedSupported -> MediaRecorder.AudioSource.UNPROCESSED
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    /**
     * Explicitly switch off any DSP the platform attached to our session. These are no-ops on
     * devices that do not expose the effect, hence the availability checks.
     */
    private fun disableVoiceProcessing(sessionId: Int): List<android.media.audiofx.AudioEffect> {
        val effects = mutableListOf<android.media.audiofx.AudioEffect>()
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.also {
                    it.enabled = false
                    effects += it
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.also {
                    it.enabled = false
                    effects += it
                }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.also {
                    it.enabled = false
                    effects += it
                }
            }
        }.onFailure { Log.w(TAG, "Could not disable voice processing", it) }
        return effects
    }

    companion object {
        private const val TAG = "AudioCaptureSource"
        private const val BYTES_PER_FLOAT = 4

        /** 44.1 kHz is the one rate every Android device supports natively for capture. */
        const val DEFAULT_SAMPLE_RATE = 44_100

        /**
         * 8192 samples = 186 ms. A bass low B (30.87 Hz) still fits ~5.7 periods in that,
         * which is the minimum the NSDF needs for a stable peak.
         */
        const val DEFAULT_WINDOW_SIZE = 8192

        /** 2048 samples = 46 ms between UI updates, about 21 readings per second. */
        const val DEFAULT_HOP_SIZE = 2048
    }
}
