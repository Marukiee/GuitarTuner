package nl.markmaaktmedia.guitartuner.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import nl.markmaaktmedia.guitartuner.R

/**
 * The haptic and the chime that fire when a string locks in.
 *
 * ## Why the chime was inaudible
 *
 * It was published on `USAGE_ASSISTANCE_SONIFICATION`, which routes to the system/notification
 * stream. That stream sits at zero on any phone kept on silent or vibrate, which is most phones,
 * and the media volume the user has turned up does nothing for it. It now plays as media, which
 * is the stream someone tuning a guitar has audible by definition.
 *
 * The second problem was a race: `SoundPool.load` is asynchronous and the first tuned string can
 * easily arrive before decoding finishes, in which case `play` is a silent no-op. The load result
 * is now tracked so a chime that is not ready yet is skipped knowingly rather than swallowed.
 *
 * The chime itself is a G6 into a C7. Both sit above the detector's ceiling even on the highest
 * instrument on the list, a violin at about 825 Hz, so the sound leaving the speaker can never be
 * picked up by the microphone and mistaken for a string.
 */
class TunerFeedback(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile
    private var chimeReady = false

    private val chimeId = soundPool
        .also { pool ->
            pool.setOnLoadCompleteListener { _, _, status ->
                chimeReady = status == 0
                if (!chimeReady) Log.w(TAG, "Chime failed to load, status $status")
            }
        }
        .load(context, R.raw.string_tuned, 1)

    private val vibratorManager: VibratorManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
        } else {
            null
        }

    private val vibrator: Vibrator? = vibratorManager?.defaultVibrator
        ?: @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun stringTuned() {
        if (chimeReady) {
            soundPool.play(chimeId, 1f, 1f, 1, 0, 1f)
        } else {
            Log.i(TAG, "Chime not decoded yet, skipping")
        }
        playSuccessHaptic()
    }

    /**
     * The end of a pass over every string.
     *
     * Deliberately not the same as one string: the chime plays twice, a fifth apart, and
     * the haptic is a third primitive longer. A finish that sounds identical to a step
     * makes the player look at the screen to find out whether they are done, which is the
     * one moment they should not have to.
     */
    fun allTuned() {
        if (chimeReady) {
            soundPool.play(chimeId, 1f, 1f, 1, 0, 1f)
            soundPool.play(chimeId, 0.9f, 0.9f, 1, 0, 1.5f)
        }
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
                .compose()
            vibrate(effect)
        } else {
            vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun playSuccessHaptic() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 70)
                .compose()
            vibrate(effect)
        } else {
            vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrate(effect: VibrationEffect) {
        val manager = vibratorManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager != null) {
            manager.vibrate(CombinedVibration.createParallel(effect))
            return
        }
        @Suppress("DEPRECATION")
        vibrator?.vibrate(effect)
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val TAG = "TunerFeedback"
    }
}
