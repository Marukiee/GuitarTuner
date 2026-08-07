package nl.markmaaktmedia.guitartuner.domain.model

/**
 * Dark mode is a setting, not just a system mirror.
 *
 * A tuner gets used on a dim stage next to a phone that is still in light mode, and the meter is
 * a lot easier to read when it is not a white rectangle in your face.
 */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}
