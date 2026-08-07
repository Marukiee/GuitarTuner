# Guitar Tuner

An open source chromatic instrument tuner for Android, built entirely in Kotlin and Jetpack
Compose with a Material 3 Expressive interface that takes its colours from your wallpaper.

No ads, no accounts, no network access except one call to the GitHub Releases API to tell you
when a new build is out. Audio never leaves the device.

## Status

Core is in place: audio capture, pitch detection, string matching, auto advance, signing and
release automation. The expressive UI (the animated tuning visualizer and the dynamic headstock)
is the next piece of work; the app currently renders a plain readout on top of the same state.

## Install

Grab the latest APK from [Releases](https://github.com/Marukiee/GuitarTuner/releases/latest).
The app tells you when a newer build exists and links straight to it, so this is a one time step.

Every release is signed with the same key, which means updates install straight over the previous
version. You never have to uninstall first.

## How it works

### Pitch detection

The detector is a hand written **McLeod Pitch Method** (MPM) implementation in
`audio/McLeodPitchDetector.kt`. No TarsosDSP, no NDK, no FFT.

MPM was chosen over YIN because a freshly plucked low string has a second harmonic that is often
louder than its fundamental, and that is exactly where YIN's cumulative mean normalisation likes
to report the octave above. MPM normalises per lag instead, so the true fundamental keeps the
taller peak. Peak picking then takes the *first* peak that clears 90 percent of the tallest one,
rather than the tallest, which is the same bias expressed a second time.

Cost is kept down by evaluating the NSDF only for lags inside the current instrument's frequency
range. A six string guitar needs roughly 5M multiply adds per window rather than the 67M a full
range autocorrelation would need. Parabolic interpolation around the winning peak takes the
resolution from one whole sample of lag (about 13 cents at E4) to well under one cent.

### Audio capture

`audio/AudioCaptureSource.kt` opens `AudioRecord` with `ENCODING_PCM_FLOAT` at 44.1 kHz and
prefers, in order, `UNPROCESSED`, then `VOICE_RECOGNITION`, then `MIC`. It additionally disables
`AutomaticGainControl`, `NoiseSuppressor` and `AcousticEchoCanceler` on its own session.

This matters more than the algorithm does. The default `MIC` source runs through the platform
voice pipeline on most phones, and AGC alone will shift a reading by several cents and cut a
decaying note short.

Windows are 8192 samples (186 ms, enough periods for a five string bass B0 at 30.87 Hz) advancing
by 2048 samples, so the UI updates about 21 times a second.

### Architecture

```
MainActivity
  └── TunerViewModel                  MVVM, StateFlow
        ├── uiState  : StateFlow<TunerUiState>     slow, user driven
        ├── reading  : StateFlow<TuningReading?>   fast, ~21 Hz
        └── events   : SharedFlow<TunerEvent>      one shot: haptic, chime, advance
              │
              ├── PitchEngine            capture + detect + median filter, on Dispatchers.Default
              │     ├── AudioCaptureSource
              │     └── McLeodPitchDetector
              ├── StringMatcher          nearest string, octave folding, switch hysteresis
              └── InTuneTracker          "held in tune for 500 ms" debounce
```

The state is split in two on purpose. Anything that reads `reading` recomposes about 21 times a
second, so the visualizer consumes it through an `Animatable` inside a `graphicsLayer` or `Canvas`
lambda. That confines the audio rate to the draw phase and never triggers recomposition or
relayout, which is what keeps the animation fluid on a mid range phone.

Smoothing is deliberately split too: a median of five in `PitchEngine` erases single frame octave
jumps, and everything visual is smoothed by Compose springs at display refresh rate rather than at
the 21 Hz analysis rate.

### Auto advance

A string counts as tuned when it stays inside 2.5 cents, with clarity above 0.9, continuously for
500 ms. Not zero cents: string inharmonicity and finger pressure make a true zero unreachable, and
2.5 cents is comfortably under the roughly 5 cent difference a trained ear can hear.

On success the app fires a two primitive haptic, plays a chime, and moves the target to the next
string by ascending pitch. By pitch, not by peg position, so a re-entrant ukulele advances
C4, E4, G4, A4 rather than following the headstock.

The chime is a G6 into a C7. Both sit far above the detector's ceiling of about 420 Hz, so the
sound coming out of the speaker can never be picked up by the microphone and mistaken for a
string.

### Supported instruments

Acoustic and electric six string, seven string, four and five string bass, ukulele. Tunings are
stored as MIDI note numbers rather than frequencies, so the reference pitch is adjustable
(A = 432 through 480 Hz) without touching a table.

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
