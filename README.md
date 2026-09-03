# Guitar Tuner

An open source chromatic instrument tuner for Android, built entirely in Kotlin and Jetpack
Compose with a Material 3 Expressive interface that takes its colours from your wallpaper.

No ads, no accounts, no network access except one call to the GitHub Releases API to tell you
when a new build is out. Audio never leaves the device.

## Status

Working end to end: band limited audio capture, pitch detection with octave locking, string
matching, auto advance, the tuning dial, per instrument headstocks, reference tones, signed
releases and in app update notification.

## Install

Grab the latest APK from [Releases](https://github.com/Marukiee/GuitarTuner/releases/latest).
The app tells you when a newer build exists and links straight to it, so this is a one time step.

Every release is signed with the same key, which means updates install straight over the previous
version. You never have to uninstall first.

## How it works

### The signal chain

Detection is only as good as what reaches it, so the microphone signal is band limited to the
band the selected instrument can actually produce before anything looks for a period.
`audio/SignalChain.kt` does this with cookbook biquads: a **6th order Butterworth high pass** just
under the lowest string and a 4th order low pass about three harmonics above the highest.

The high pass is the part that matters. Everything below a guitar's low E is mains hum, handling
noise and the rumble a phone microphone generates on its own, and all of it lands in exactly the
lag range where an autocorrelator hunts for a bass note. It is why a tuner sometimes reports a
confident 45 Hz from an empty room. It is 6th order rather than 4th because the interferer sits
close to the corner: 50 Hz hum is four semitones under a guitar's low E, where a 4th order skirt
leaves it at only -7 dB. The third biquad takes it to -15 dB for one more multiply add per sample.

The corners move with the instrument, which is what lets them be aggressive: 66 Hz for a guitar,
25 Hz for a five string bass that really does go down to 31 Hz.

The low pass then pays for itself twice. A plucked string has energy well past 5 kHz, periodic at
the fundamental but with its own narrow structure, and that structure puts small ripples on the
NSDF that peak picking can mistake for a period. Removing it also makes **4x decimation** free:
44.1 kHz drops to 11.025 kHz, which cuts the NSDF by a factor of sixteen. That is what pays for
the long window a low B needs.

### Pitch detection

The detector is a hand written **McLeod Pitch Method** (MPM) implementation in
`audio/McLeodPitchDetector.kt`, running on the decimated stream. No TarsosDSP, no NDK, no FFT.

MPM was chosen over YIN because a freshly plucked low string has a second harmonic that is often
louder than its fundamental, and that is exactly where YIN's cumulative mean normalisation likes
to report the octave above. MPM normalises per lag instead, so the true fundamental keeps the
taller peak. Peak picking then takes the *first* peak that clears 90 percent of the tallest one,
rather than the tallest, which is the same bias expressed a second time.

Cost is kept down further by evaluating the NSDF only for lags inside the current instrument's
frequency range. Parabolic interpolation around the winning peak takes the resolution from one
whole sample of lag to well under one cent.

### Making the reading trustworthy

MPM still slips an octave, and it does it most on the first frames of a note, which is exactly
when the display is being watched. `audio/PitchStabiliser.kt` sits between the detector and the
UI and does four things, in order:

1. **A median of five**, which erases single frame outliers without lagging a real move.
2. **An octave anchor.** A slow running estimate of where the note actually is; each new reading
   is tested at ratios 1, 1/2, 2, 1/3 and 3 and snapped to whichever lands within 320 cents of
   the anchor. The idea is borrowed from the onset locking in 29a.ch's tuner: the attack of a
   pluck has by far the best signal to noise ratio, so a loud new onset (7 dB above the decaying
   peak) resets the anchor and everything after it is held to that estimate.
3. **Adaptive smoothing in log frequency**, so the time constant is constant in *cents* rather
   than in Hz. Small movements are smoothed hard, a genuine tuning peg turn is followed quickly.
4. **A settle test.** A reading is marked `settled` only once four consecutive frames agree
   within 12 cents. Nothing downstream acts on an unsettled reading: it cannot move the auto
   mode target, it cannot start the in tune clock, and it cannot count as tuned.

That last flag is the difference between a tuner that jumps to the wrong string on the attack
transient and one that waits the extra 100 ms.

### Audio capture

`audio/AudioCaptureSource.kt` opens `AudioRecord` with `ENCODING_PCM_FLOAT` at 44.1 kHz and
disables `AutomaticGainControl`, `NoiseSuppressor` and `AcousticEchoCanceler` on its own session.

This matters more than the algorithm does: the platform voice pipeline is on by default, and AGC
alone will shift a reading by several cents and cut a decaying note short. Which capture source
is opened is a separate question, covered under [Microphone selection](#microphone-selection).

Capture hops 2048 samples at 44.1 kHz, which becomes 512 samples at the decimated 11.025 kHz, so
the UI updates about 21 times a second.

The analysis window is sized per instrument rather than fixed: seven periods of the lowest note,
rounded to a power of two and clamped to 1024-4096 samples of decimated audio. A violin's low G
needs 65 ms of audio and a five string bass's low B needs 370 ms, and a single window length
either makes the violin sluggish or makes the bass unreliable.

### Architecture

```
MainActivity
  └── TunerViewModel                  MVVM, StateFlow
        ├── uiState  : StateFlow<TunerUiState>     slow, user driven
        ├── reading  : StateFlow<TuningReading?>   fast, ~21 Hz
        └── events   : SharedFlow<TunerEvent>      one shot: haptic, chime, advance
              │
              ├── PitchEngine            capture + filter + detect, on Dispatchers.Default
              │     ├── AudioCaptureSource     raw blocks from AudioRecord
              │     ├── DecimatingPreFilter    band limit, then 44.1 -> 11.025 kHz
              │     ├── McLeodPitchDetector    NSDF over the instrument's lag range
              │     └── PitchStabiliser        median, octave anchor, smoothing, settle
              ├── StringMatcher          nearest string, octave folding, switch hysteresis
              ├── InTuneTracker          "held in tune for 500 ms" debounce
              └── ReferenceTonePlayer    additive synthesis pluck, listening muted while it plays
```

The state is split in two on purpose. Anything that reads `reading` recomposes about 21 times a
second, so the dial consumes it through an `Animatable` inside a `graphicsLayer` or `Canvas`
lambda. That confines the audio rate to the draw phase and never triggers recomposition or
relayout, which is what keeps the animation fluid on a mid range phone.

Smoothing is deliberately split too: `PitchStabiliser` does the signal work at the 21 Hz analysis
rate, and everything *visual* is smoothed separately by Compose springs at display refresh rate,
so the two never fight each other.

### Auto advance

A string counts as tuned when it stays inside 5 cents, with clarity above 0.82 and the reading
marked settled, for 500 ms, tolerating up to three frames outside the window before the clock
restarts.

Every one of those numbers started stricter and had to be relaxed, because a plucked string is
not a laboratory signal: it wobbles for the first few hundred milliseconds and the detector drops
the odd frame as the note decays. At 2.5 cents with zero tolerance the hold essentially never
completed.

On success the app fires a two primitive haptic, plays a chime, and moves the target to the next
string by ascending pitch. By pitch, not by peg position, so a re-entrant ukulele advances
C4, E4, G4, A4 rather than following the headstock.

Finishing every string is deliberately not the same sound: the chime plays twice, a fifth apart,
with a longer haptic. A finish that sounds identical to a step makes the player look at the
screen to find out whether they are done, which is the one moment they should not have to.

The chime is a G6 into a C7. Both sit above the detector's ceiling even on a violin (about 825
Hz), so the sound coming out of the speaker can never be picked up by the microphone and mistaken
for a string.

### Reference tones

Tapping the tone button plays the target string so it can be tuned by ear.
`audio/ReferenceTonePlayer.kt` synthesises it additively into a static `AudioTrack`: eight
harmonics at falling gains, each decaying faster than the one below it, which is roughly what a
plucked string does and reads as an instrument rather than a beep. Listening is muted while it
plays plus 250 ms, because a tuner that hears its own reference tone reports a perfect note
regardless of the instrument.

### Keeping the animation fluid

The reading flow drives three `Animatable`s, and every one of them is read *inside* a `Canvas`
draw lambda or a `graphicsLayer` block. A state read in either place invalidates only the draw
phase, so 21 readings a second never reach composition or layout, and the springs interpolate at
display refresh rate rather than at the analysis rate.

`collectLatest` cancels the previous `animateTo` when a new reading lands. A cancelled
`Animatable` keeps its velocity, so retargeting 21 times a second reads as one continuous motion
instead of a series of restarts. The needle spring is deliberately under damped (0.62) so it
overshoots slightly and settles back.

The dial is a 200 degree arc with the target at the top, ticks every 5 cents and a needle that
runs across it. Colour is a ramp rather than a threshold: the needle and the note both lerp from
the in tune colour towards flat or sharp with distance, so how close you are is legible before
any number is read. The hold that confirms a string grows as a ring from the top, symmetrically
in both directions.

The only thing that genuinely recomposes is the readout text, isolated in its own composable and
fed by `map { }.distinctUntilChanged()` so 21 readings a second collapse to the handful of frames
where the whole cents value actually changed.

### Design

The visual language is shared with MarkMySteps and Local AI, deliberately and down to the
details: the same Indigo and Rose ramps, the same continuous-corner squircles, the same grouped
slabs (24dp outer radius, 4dp inner, 2dp between rows), the same split between springs for motion
and tweens for colour, and the same 0.96 press dip on anything tappable.

Dark mode is pure black by default rather than a dark grey, with containers stepping up a tinted
grey ladder on top of it, which is what an OLED panel is for. Wallpaper colours are on by default
and the whole `ColorScheme` crossfades rather than snapping when the theme changes. The launcher
icon is a tuning fork on the same slate the other two apps use, and its mark tracks the system
accent from API 31 up.

### Typography

Google Sans Flex as a single **variable** font, bundled rather than pulled through the
downloadable-fonts provider. Weights come from the `wght` axis, and every style also pins
`ROND` to 100, which is the rounded end of the roundness axis and what gives the app the same
voice as MarkMySteps and Local AI. It is on Google Fonts under the OFL (see
`LICENSE-GoogleSansFlex.txt`), and bundling means no dependency on Play Services and no
first-frame flash while a font request resolves.

Icons are Material Symbols Rounded, checked in as vector drawables and reached through
`ui/theme/TunerIcons.kt`, rather than the `material-icons-extended` artifact, which is a few
thousand icons to ship two dozen.

### Supported instruments

Ten instruments, 32 tunings: acoustic and electric six string, seven string, four and five string
bass, ukulele, banjo, mandolin, violin and cello. Alternates include drop D, DADGAD, open G and D,
half and whole step down, drop C, low G and baritone ukulele, double C and G modal banjo, and
viola.

Tunings are stored as MIDI note numbers rather than frequencies, so the reference pitch is
adjustable (A = 400 through 480 Hz) without touching a table. Re-entrant tunings such as a
standard ukulele are handled explicitly: auto advance walks strings by ascending *pitch*, not by
peg position, and the picker says so.

Each instrument draws its own headstock in `ui/components/HeadstockView.kt` from a peg layout
(three per side, in line, four plus three, two per side, banjo, paired four, scroll) and a scale.
The scale is not cosmetic: a four string bass and a ukulele share a two-per-side layout and a
string count, so without it they would draw identically, and a test asserts every instrument has
a distinct `(layout, string count, scale)` signature.

### Microphone selection

The capture source decides which *physical* microphone the recorder is wired to, and they are not
interchangeable. UNPROCESSED and VOICE_RECOGNITION usually select a secondary capsule, often the
one beside the rear camera; MIC is the primary one at the bottom of the phone.

UNPROCESSED leads where the device advertises support for it through
`PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`, since it is the only source guaranteed to bypass all
platform processing; VOICE_RECOGNITION, MIC and CAMCORDER follow. Where the device does not
advertise it, it is not offered first, because a clean signal from a microphone that does not
work is worth nothing.

Capture rotates through the available sources when one delivers nothing for two seconds, and the
choice can be pinned in Settings. Pinning is stored as null until the user actually picks one, so
an untouched install never has the automatic fallback disabled by a default. There is an input level meter under the tuning meter for the
same reason: without it, a dead microphone and a silent room look identical from outside the app.

## Build

Requires JDK 21 and the Android SDK.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

For a signed local release build, create `keystore.properties` in the repository root. It is git
ignored, and the keystore itself must live outside the repository:

```properties
storeFile=/home/you/.android/keystores/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

CI does the same thing from four repository secrets: `ANDROID_KEYSTORE_B64` (the keystore, base64
encoded), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.

Every push to `main` builds, tags `v<run number>`, and publishes the APK under the stable name
`guitartuner.apk`. `versionCode` is set to the same run number, which is what the in app update
check compares against.

## Licence

MIT. See [LICENSE](LICENSE).
