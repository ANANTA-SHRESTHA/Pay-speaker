package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.preferences.PaymentChannel

@Immutable
data class PaySpeakerExtendedColors(
    val hardwareStart: Color,
    val hardwareEnd: Color,
    val hardwareAccent: Color,
    val hardwareOnSurface: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningIcon: Color,
)

private val LightExtendedColors = PaySpeakerExtendedColors(
    hardwareStart = EmeraldDeep,
    hardwareEnd = EmeraldObsidian,
    hardwareAccent = Emerald80,
    hardwareOnSurface = Color.White,
    warningContainer = AmberContainerLight,
    onWarningContainer = AmberOnContainerLight,
    warningIcon = AmberAccent,
)

private val DarkExtendedColors = PaySpeakerExtendedColors(
    hardwareStart = Color(0xFF0A3D28),
    hardwareEnd = Color(0xFF04120C),
    hardwareAccent = Emerald80,
    hardwareOnSurface = Color(0xFFECFDF5),
    warningContainer = AmberContainerDark,
    onWarningContainer = AmberOnContainerDark,
    warningIcon = AmberAccent,
)

val LocalPaySpeakerColors = staticCompositionLocalOf { LightExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = Emerald80,
    onPrimary = EmeraldOnContainer,
    primaryContainer = Color(0xFF0B4F30),
    onPrimaryContainer = EmeraldSoft,
    secondary = SlateTeal80,
    onSecondary = EmeraldOnContainer,
    secondaryContainer = Color(0xFF1A3829),
    onSecondaryContainer = EmeraldSoft,
    tertiary = AmberAccent,
    onTertiary = AmberOnContainerLight,
    background = CarbonBackgroundDark,
    onBackground = Color(0xFFE7F3EC),
    surface = CarbonSurfaceDark,
    onSurface = Color(0xFFE7F3EC),
    surfaceVariant = CarbonSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFA7C4B5),
    surfaceContainerLow = Color(0xFF0E1813),
    surfaceContainer = Color(0xFF14221B),
    surfaceContainerHigh = Color(0xFF1C2E25),
    outline = Color(0xFF2D483A),
    outlineVariant = Color(0xFF21372C),
    error = ErrorRedDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald40,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = SlateTeal40,
    onSecondary = Color.White,
    secondaryContainer = MintContainerLight,
    onSecondaryContainer = Color(0xFF0D3321),
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = MintBackgroundLight,
    onBackground = Color(0xFF0D1F16),
    surface = Color.White,
    onSurface = Color(0xFF0D1F16),
    surfaceVariant = MintSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF425E50),
    surfaceContainerLow = Color(0xFFF8FCFA),
    surfaceContainer = MintContainerLight,
    surfaceContainerHigh = Color(0xFFD7EDE1),
    outline = Color(0xFFAECBB9),
    outlineVariant = Color(0xFFD4E6DC),
    error = ErrorRedLight,
)

val PaySpeakerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PaySpeakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalPaySpeakerColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PaySpeakerTypography,
            shapes = PaySpeakerShapes,
            content = content,
        )
    }
}

fun channelAccentColor(channel: PaymentChannel): Color = when (channel) {
    PaymentChannel.ESEWA -> ChannelEsewa
    PaymentChannel.KHALTI -> ChannelKhalti
    PaymentChannel.FONEPAY -> ChannelFonepay
    PaymentChannel.IME_PAY -> ChannelImePay
    PaymentChannel.CONNECT_IPS -> ChannelConnectIps
    PaymentChannel.BANK_ALERT -> ChannelBank
    PaymentChannel.OTHER_WALLET -> ChannelOther
}
