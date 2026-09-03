package nl.markmaaktmedia.guitartuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays the target note, so a string can be tuned by ear against it.
 *
 * Every tuner people actually like has this, and it is the only way to tune a string that
 * is so far out that the meter is not helping yet: you get within a semitone by ear in
 * two seconds, then the meter takes over.
 *
 * The tone is additive rather than a plain sine, and that is not decoration. A sine at 82
 * Hz on a phone speaker is nearly inaudible, because the speaker has no output that low;
 * what you hear is its harmonics. Synthesising the harmonics deliberately, each decaying
 * faster than the one below it the way a real string's do, gives a sound that carries on a
 * phone speaker and still has its pitch unambiguously at the fundamental.
 *
 * Generated as PCM16 into a static [AudioTrack] rather than streamed: the whole tone is
 * under two seconds, so writing it once and letting the hardware play it costs nothing and
 * cannot glitch.
 */
class ReferenceTonePlayer(private val sampleRate: Int = 44_100) {

    private var track: AudioTrack? = null

    /**
     * @param frequencyHz exact target frequency, already resolved against the reference pitch.
     * @return how long the tone will sound, so the caller can mute the microphone for it.
     */
    fun play(frequencyHz: Float, durationMillis: Int = DEFAULT_DURATION_MS): Int {
        stop()
        if (frequencyHz <= 0f) return 0

        val frames = sampleRate * durationMillis / 1000
        val samples = ShortArray(frames)

        val twoPiOverRate = 2.0 * PI / sampleRate
        // Enough harmonics to carry on a phone speaker, stopping short of Nyquist.
        val partials = HARMONIC_GAINS.size

        var peak = 0.0
        val raw = DoubleArray(frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            var value = 0.0
            for (h in 0 until partials) {
                val harmonic = h + 1
                val partialHz = frequencyHz * harmonic
                if (partialHz > sampleRate / 2.2) break
                // Higher partials die first, which is most of what makes a string a string.
                val decay = exp(-t / (BASE_TAU_SECONDS / harmonic))
                value += HARMONIC_GAINS[h] * decay * sin(twoPiOverRate * partialHz * i)
            }
            raw[i] = value
            val magnitude = if (value < 0) -value else value
            if (magnitude > peak) peak = magnitude
        }
        if (peak <= 0.0) return 0

        val attackFrames = sampleRate * ATTACK_MS / 1000
        val fadeFrames = sampleRate * FADE_MS / 1000
        val normalise = 0.82 / peak

        for (i in 0 until frames) {
            // A hard start or stop on a decaying tone is an audible click.
            val attack = if (i < attackFrames) i.toDouble() / attackFrames else 1.0
            val fade = if (i > frames - fadeFrames) (frames - i).toDouble() / fadeFrames else 1.0
            val value = raw[i] * normalise * attack * fade
            samples[i] = (value * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        created.write(samples, 0, samples.size)
        created.play()
        track = created
        return durationMillis
    }

    fun stop() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }

    fun release() = stop()

    private companion object {
        const val DEFAULT_DURATION_MS = 1800

        /** Relative level of each harmonic, fundamental first. */
        val HARMONIC_GAINS = doubleArrayOf(1.0, 0.62, 0.42, 0.26, 0.17, 0.11, 0.07, 0.05)

        /** Decay time of the fundamental. Each harmonic above it dies proportionally faster. */
        const val BASE_TAU_SECONDS = 1.1

        const val ATTACK_MS = 6
        const val FADE_MS = 90
    }
}
