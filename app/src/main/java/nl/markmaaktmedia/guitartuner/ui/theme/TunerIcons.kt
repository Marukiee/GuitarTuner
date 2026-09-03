package nl.markmaaktmedia.guitartuner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import nl.markmaaktmedia.guitartuner.R

/**
 * Every icon in the app, in one place.
 *
 * These are Material Symbols in the Rounded style, shipped as vector drawables rather
 * than pulled from `Icons.Rounded`. The bundled Compose set is the older Material Icons
 * drawing, with squarer joins and thinner strokes, and next to a rounded typeface it
 * reads as another app's icons. Symbols Rounded matches the terminals of the type, which
 * is what makes the whole thing look drawn by one hand.
 *
 * A side effect worth having: nothing pulls in `material-icons-extended`, a few thousand
 * vectors compiled into the APK to use a dozen of them.
 */
object TunerIcons {

    // Chrome
    val Back: Painter @Composable get() = painterResource(R.drawable.sym_arrow_back)
    val Close: Painter @Composable get() = painterResource(R.drawable.sym_close)
    val Settings: Painter @Composable get() = painterResource(R.drawable.sym_tune)
    val SettingsFilled: Painter @Composable get() = painterResource(R.drawable.sym_tune_filled)
    val ChevronRight: Painter @Composable get() = painterResource(R.drawable.sym_chevron_right)
    val ChevronDown: Painter @Composable get() = painterResource(R.drawable.sym_keyboard_arrow_down)
    val Check: Painter @Composable get() = painterResource(R.drawable.sym_check)
    val CheckCircle: Painter @Composable get() = painterResource(R.drawable.sym_check_circle)
    val CheckCircleFilled: Painter @Composable get() = painterResource(R.drawable.sym_check_circle_filled)
    val Info: Painter @Composable get() = painterResource(R.drawable.sym_info)
    val Error: Painter @Composable get() = painterResource(R.drawable.sym_error)
    val OpenInNew: Painter @Composable get() = painterResource(R.drawable.sym_open_in_new)
    val Refresh: Painter @Composable get() = painterResource(R.drawable.sym_refresh)
    val Add: Painter @Composable get() = painterResource(R.drawable.sym_add)
    val Remove: Painter @Composable get() = painterResource(R.drawable.sym_remove)

    // Tuner
    val TuneUp: Painter @Composable get() = painterResource(R.drawable.sym_arrow_upward)
    val TuneDown: Painter @Composable get() = painterResource(R.drawable.sym_arrow_downward)
    val Auto: Painter @Composable get() = painterResource(R.drawable.sym_bolt)
    val AutoFilled: Painter @Composable get() = painterResource(R.drawable.sym_bolt_filled)
    val ReferenceTone: Painter @Composable get() = painterResource(R.drawable.sym_volume_up)
    val Restart: Painter @Composable get() = painterResource(R.drawable.sym_restart_alt)
    val Level: Painter @Composable get() = painterResource(R.drawable.sym_graphic_eq)
    val Note: Painter @Composable get() = painterResource(R.drawable.sym_music_note)
    val Mic: Painter @Composable get() = painterResource(R.drawable.sym_mic)
    val MicOff: Painter @Composable get() = painterResource(R.drawable.sym_mic_off)
    val Tuning: Painter @Composable get() = painterResource(R.drawable.sym_instant_mix)
    val Calibration: Painter @Composable get() = painterResource(R.drawable.sym_straighten)
    val Sensitivity: Painter @Composable get() = painterResource(R.drawable.sym_speed)

    // Settings sections
    val Palette: Painter @Composable get() = painterResource(R.drawable.sym_palette)
    val DarkMode: Painter @Composable get() = painterResource(R.drawable.sym_dark_mode)
    val LightMode: Painter @Composable get() = painterResource(R.drawable.sym_light_mode)
    val Contrast: Painter @Composable get() = painterResource(R.drawable.sym_contrast)
    val Update: Painter @Composable get() = painterResource(R.drawable.sym_system_update)
    val Download: Painter @Composable get() = painterResource(R.drawable.sym_download)
    val Code: Painter @Composable get() = painterResource(R.drawable.sym_code)
    val Shield: Painter @Composable get() = painterResource(R.drawable.sym_shield)
    val Idea: Painter @Composable get() = painterResource(R.drawable.sym_lightbulb)
}
