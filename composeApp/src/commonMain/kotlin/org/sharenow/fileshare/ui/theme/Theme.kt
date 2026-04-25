package org.sharenow.fileshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PrimaryBlue = Color(0xFF8B5CF6) // Violet
val SecondaryBlue = Color(0xFF3B82F6) // Bright Blue
val AccentGreen = Color(0xFF10B981) // Emerald
val BackgroundLight = Color(0xFF0F172A) // Deep Slate 900
val SurfaceWhite = Color(0xFF1E293B) // Slate 800
val TextDark = Color(0xFFF8FAFC) // Slate 50
val TextGray = Color(0xFF94A3B8) // Slate 400
val ShadowColor = Color(0x66000000)
val SoftGray = Color(0xFF334155) // Slate 700

// For backward compatibility with existing code during migration
val PrimaryNeon = PrimaryBlue
val SecondaryNeon = SecondaryBlue
val AccentNeon = AccentGreen
val GlassWhite = Color(0xCCFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryBlue,
    background = BackgroundLight,
    surface = SurfaceWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextDark,
    onSurface = TextDark,
)

val ModernTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
)

@Composable
fun FuturisticTheme(
    darkTheme: Boolean = false, // Always light theme as requested
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = ModernTypography,
        content = content
    )
}
