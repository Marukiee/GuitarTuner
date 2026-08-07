package nl.markmaaktmedia.guitartuner.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import nl.markmaaktmedia.guitartuner.R

/**
 * The haptic and the chime that fire when a string locks in.
 *
 * Two things worth knowing:
 *
 * - The chime is a G6 into a C7. Both sit far above the detector's ~420 Hz ceiling, so the sound
 *   leaving the speaker can never be picked up by the microphone and mistaken for a string. That
 *   is cheaper and more reliable than muting detection for a few hundred milliseconds.
 * - The haptic is a two primitive composition (a click followed by a lighter tick) rather than a
 *   flat buzz, so it reads as "done" instead of "error" on devices with a decent actuator.
 */
class TunerFeedback(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val chimeId = soundPool.load(context, R.raw.string_tuned, 1)

    private val vibratorManager: VibratorManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
        } else {
            null
        }

    private val vibrator: Vibrator? = vibratorManager?.defaultVibrator
        ?: @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun stringTuned() {
        soundPool.play(chimeId, 0.55f, 0.55f, 1, 0, 1f)
        playSuccessHaptic()
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
}
