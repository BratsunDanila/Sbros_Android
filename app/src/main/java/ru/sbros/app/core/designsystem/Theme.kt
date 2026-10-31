package ru.sbros.app.core.designsystem

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import ru.sbros.app.R

data class SbrosColors(
    val primary: Color, val background: Color, val ink: Color, val muted: Color,
    val muted2: Color, val soft: Color, val border: Color, val accent: Color,
    val good: Color = Color(0xFF28774E), val card: Color = Color.White,
    val onPrimary: Color = Color.White,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)
fun palette(name: String, dark: Boolean): SbrosColors = if (dark) when(name) {
    "sage" -> SbrosColors(c(0xA2CDA8),c(0x121712),c(0xEEF5ED),c(0x8D9C8C),c(0xB4C7B6),c(0x243026),c(0x354338),c(0x304C36),c(0x90DEB0),c(0x1B221D),c(0x162A1C))
    "clay" -> SbrosColors(c(0xE1B89D),c(0x191310),c(0xFBF1E9),c(0xA09084),c(0xCDB7A7),c(0x352A24),c(0x49392F),c(0x503B2E),c(0x90DEB0),c(0x241C17),c(0x382217))
    "ocean" -> SbrosColors(c(0x9DCDD9),c(0x10171B),c(0xEDF5F8),c(0x879CA6),c(0xAEC4CF),c(0x22313A),c(0x304450),c(0x294C5C),c(0x90DEB0),c(0x182329),c(0x13313C))
    else -> SbrosColors(c(0xC1BAFF),c(0x121118),c(0xF4F1FC),c(0x918CA3),c(0xB7B0C6),c(0x292632),c(0x36323F),c(0x383252),c(0x90DEB0),c(0x1C1A24),c(0x211C42))
} else when(name) {
    "sage" -> SbrosColors(c(0x49755A),c(0xF3F7F1),c(0x1E2921),c(0x879486),c(0x5A705D),c(0xE8EFE7),c(0xDCE8D9),c(0xDEEDDF),c(0x326C47))
    "clay" -> SbrosColors(c(0x926249),c(0xF9F5F1),c(0x30251F),c(0x998A82),c(0x735F55),c(0xF3EAE3),c(0xE9DED6),c(0xF1E2D7),c(0x487B56))
    "ocean" -> SbrosColors(c(0x3A7485),c(0xF2F7F9),c(0x1C2A30),c(0x83959D),c(0x5B717B),c(0xE8F0F4),c(0xD9E7ED),c(0xDCEEF6),c(0x247858))
    else -> SbrosColors(c(0x5A528D),c(0xF5F3FB),c(0x211F2C),c(0x89859B),c(0x625D73),c(0xEEEBF8),c(0xE3DFEE),c(0xE8E4FF))
}
val LocalSbrosColors = staticCompositionLocalOf { palette("lavender", false) }
// Separate static faces avoid OEM loaders that ignore variation settings on a shared file.
// Generated from the original Onest outlines; see scripts/Generate-OnestStatic.py.
val Onest = FontFamily(
    Font(R.font.onest_400, FontWeight.Normal),
    Font(R.font.onest_500, FontWeight.Medium),
    Font(R.font.onest_600, FontWeight.SemiBold),
    Font(R.font.onest_650, FontWeight(650)),
    Font(R.font.onest_700, FontWeight.Bold),
    Font(R.font.onest_750, FontWeight(750)),
    Font(R.font.onest_760, FontWeight(760)),
    Font(R.font.onest_800, FontWeight.ExtraBold),
)
@Composable fun SbrosTheme(name: String, dark: Boolean, content: @Composable () -> Unit) {
    val colors = palette(name, dark)
    val scheme = if(dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalSbrosColors provides colors) {
        MaterialTheme(
            colorScheme = scheme.copy(primary=colors.primary,onPrimary=colors.onPrimary,background=colors.background,surface=colors.card,onSurface=colors.ink,onBackground=colors.ink,outline=colors.border,surfaceVariant=colors.soft,
                surfaceTint=colors.primary,onSurfaceVariant=colors.muted2,primaryContainer=colors.accent,onPrimaryContainer=colors.ink,
                secondary=colors.primary,onSecondary=colors.onPrimary,secondaryContainer=colors.soft,onSecondaryContainer=colors.ink),
            typography = Typography(bodyLarge=TextStyle(fontFamily=Onest,fontSize=13.sp), bodyMedium=TextStyle(fontFamily=Onest,fontSize=12.sp)),
            content = content,
        )
    }
}
