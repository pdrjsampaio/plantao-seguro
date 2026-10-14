package com.plantaoseguro.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plantaoseguro.R

/**
 * Identidade: a luz de viatura (giroflex) e as cores de alerta da Defesa
 * Civil — azul de plantão noturno e laranja de sinalização. O laranja é
 * reservado para "plantão valendo / sirene"; o resto da interface fica calmo.
 */
object Paleta {
    val Noite = Color(0xFF0F1B33)
    val NoiteFunda = Color(0xFF0A1326)
    val NoiteSuperficie = Color(0xFF15233F)
    val NoiteElevada = Color(0xFF1C2D4F)
    val NoiteBorda = Color(0xFF2A3D63)

    val Sinal = Color(0xFFFF6A13)
    val SinalClaro = Color(0xFFFF9A4D)
    val Ambar = Color(0xFFFFB21A)

    val Papel = Color(0xFFF0F3F7)
    val PapelElevado = Color(0xFFE6EBF2)
    val PapelBorda = Color(0xFFD3DBE6)
    val Tinta = Color(0xFF16203A)
    val TintaSuave = Color(0xFF56627A)

    val Radio = Color(0xFF14935F)
    val RadioClaro = Color(0xFF4FD39A)
    val Alarme = Color(0xFFE0262F)
    val AlarmeClaro = Color(0xFFFF6B6B)

    /** Cores das faixas de cada plantão na escala. */
    val escala = listOf(
        Color(0xFFFF6A13), // laranja sinal
        Color(0xFF2F6BFF), // azul cobalto
        Color(0xFF0FA37F), // verde rádio
        Color(0xFFD1347F), // magenta
        Color(0xFFE5A100), // âmbar
        Color(0xFF7A55E6)  // violeta
    )

    fun corEscala(indice: Int): Color = escala[((indice % escala.size) + escala.size) % escala.size]
}

/** Cores que o Material não tem nome para: estado do plantão, ok, alerta. */
@Immutable
data class CoresExtras(
    val sinal: Color,
    val noSinal: Color,
    val ok: Color,
    val aviso: Color,
    val palco: Color,
    val noPalco: Color,
    val noPalcoSuave: Color,
    val linha: Color
)

val LocalCoresExtras = staticCompositionLocalOf {
    CoresExtras(Paleta.Sinal, Color.White, Paleta.Radio, Paleta.Ambar, Paleta.Noite, Color.White, Color(0xFFB8C4DA), Paleta.PapelBorda)
}

private val Claro = lightColorScheme(
    primary = Paleta.Noite,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E3F7),
    onPrimaryContainer = Paleta.Noite,
    secondary = Paleta.Sinal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D2),
    onSecondaryContainer = Color(0xFF5A2200),
    tertiary = Paleta.Radio,
    onTertiary = Color.White,
    background = Paleta.Papel,
    onBackground = Paleta.Tinta,
    surface = Paleta.Papel,
    onSurface = Paleta.Tinta,
    surfaceVariant = Paleta.PapelElevado,
    onSurfaceVariant = Paleta.TintaSuave,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3F6FA),
    surfaceContainerHighest = Paleta.PapelElevado,
    outline = Color(0xFFB4BFCE),
    outlineVariant = Paleta.PapelBorda,
    error = Paleta.Alarme,
    onError = Color.White
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0A1A3D),
    primaryContainer = Paleta.NoiteElevada,
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Paleta.SinalClaro,
    onSecondary = Color(0xFF3A1500),
    secondaryContainer = Color(0xFF4A2410),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Paleta.RadioClaro,
    onTertiary = Color(0xFF00391F),
    background = Paleta.NoiteFunda,
    onBackground = Color(0xFFE6ECF6),
    surface = Paleta.NoiteFunda,
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Paleta.NoiteElevada,
    onSurfaceVariant = Color(0xFFA3B0C8),
    surfaceContainerLowest = Color(0xFF08101F),
    surfaceContainerLow = Color(0xFF0E1A31),
    surfaceContainer = Paleta.NoiteSuperficie,
    surfaceContainerHigh = Paleta.NoiteElevada,
    surfaceContainerHighest = Color(0xFF233661),
    outline = Color(0xFF51648C),
    outlineVariant = Paleta.NoiteBorda,
    error = Paleta.AlarmeClaro,
    onError = Color(0xFF450006)
)

private val Barlow = FontFamily(
    Font(R.font.barlow_400, FontWeight.Normal),
    Font(R.font.barlow_500, FontWeight.Medium),
    Font(R.font.barlow_600, FontWeight.SemiBold),
    Font(R.font.barlow_700, FontWeight.Bold)
)

/** Largura condensada, de placa de sinalização: horários e títulos grandes. */
val BarlowCondensada = FontFamily(
    Font(R.font.barlow_condensed_600, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_700, FontWeight.Bold)
)

private val Tipos = Typography(
    displayLarge = TextStyle(fontFamily = BarlowCondensada, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = BarlowCondensada, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = BarlowCondensada, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = BarlowCondensada, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensada, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

/** Horários grandes, em número tabular para não "pular" quando mudam. */
val EstiloHorario = TextStyle(
    fontFamily = BarlowCondensada,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "tnum"
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PlantaoTema(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    val extras = if (escuro) {
        CoresExtras(
            sinal = Paleta.Sinal, noSinal = Color.White, ok = Paleta.RadioClaro, aviso = Paleta.Ambar,
            palco = Paleta.NoiteSuperficie, noPalco = Color.White, noPalcoSuave = Color(0xFFAFBCD6),
            linha = Paleta.NoiteBorda
        )
    } else {
        CoresExtras(
            sinal = Paleta.Sinal, noSinal = Color.White, ok = Paleta.Radio, aviso = Color(0xFFB87800),
            palco = Paleta.Noite, noPalco = Color.White, noPalcoSuave = Color(0xFFB8C4DA),
            linha = Paleta.PapelBorda
        )
    }
    CompositionLocalProvider(LocalCoresExtras provides extras) {
        MaterialTheme(
            colorScheme = if (escuro) Escuro else Claro,
            typography = Tipos,
            shapes = Formas,
            content = conteudo
        )
    }
}

object Extras {
    val cores: CoresExtras
        @Composable get() = LocalCoresExtras.current
}
