package com.plantaoseguro.ui.comum

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plantaoseguro.ui.tema.Paleta

enum class EstadoGiroflex { DESLIGADO, ARMADO, TOCANDO }

/**
 * A luz de viatura que resume o estado do app num relance:
 * apagada fora do plantão, girando em laranja quando a sirene está armada,
 * vermelha e rápida quando está tocando.
 */
@Composable
fun Giroflex(
    estado: EstadoGiroflex,
    modifier: Modifier = Modifier,
    tamanho: Dp = 112.dp,
    fundoEscuro: Boolean = true,
    animar: Boolean = true
) {
    val transicao = rememberInfiniteTransition(label = "giroflex")
    val giro by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (estado == EstadoGiroflex.TOCANDO) 900 else 2600, easing = LinearEasing)
        ),
        label = "giro"
    )
    val pulso by transicao.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "pulso"
    )
    val angulo = if (animar) giro else 35f
    val intensidade = if (animar) pulso else 1f

    Canvas(modifier.size(tamanho)) {
        val u = size.minDimension / 100f
        val centro = Offset(50f * u, 50f * u)

        when (estado) {
            EstadoGiroflex.DESLIGADO -> Unit
            EstadoGiroflex.ARMADO -> {
                brilho(centro, 48f * u, Paleta.Sinal.copy(alpha = 0.34f * intensidade))
                feixes(centro, 50f * u, angulo, Paleta.Ambar.copy(alpha = 0.42f))
            }
            EstadoGiroflex.TOCANDO -> {
                brilho(centro, 50f * u, Paleta.Alarme.copy(alpha = 0.5f * intensidade))
                feixes(centro, 50f * u, angulo, Color(0xFFFFD0B0).copy(alpha = 0.55f))
            }
        }

        // Cúpula
        val cupula = Path().apply {
            moveTo(27f * u, 74f * u)
            lineTo(27f * u, 47f * u)
            arcTo(Rect(27f * u, 24f * u, 73f * u, 70f * u), 180f, 180f, false)
            lineTo(73f * u, 74f * u)
            close()
        }
        val corCupula = when (estado) {
            EstadoGiroflex.DESLIGADO -> if (fundoEscuro) {
                Brush.verticalGradient(listOf(Color(0xFF3A4E78), Color(0xFF243656)), 24f * u, 74f * u)
            } else {
                Brush.verticalGradient(listOf(Color(0xFFD6DEEA), Color(0xFFAEBBCF)), 24f * u, 74f * u)
            }
            EstadoGiroflex.ARMADO ->
                Brush.verticalGradient(listOf(Color(0xFFFFC94D), Paleta.Sinal), 24f * u, 74f * u)
            EstadoGiroflex.TOCANDO ->
                Brush.verticalGradient(listOf(Color(0xFFFFB08A), Paleta.Alarme), 24f * u, 74f * u)
        }
        drawPath(cupula, corCupula)

        // Núcleo de luz quando aceso
        if (estado != EstadoGiroflex.DESLIGADO) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f * intensidade),
                topLeft = Offset(44f * u, 40f * u),
                size = Size(12f * u, 30f * u),
                cornerRadius = CornerRadius(6f * u)
            )
        }
        // Reflexo do vidro
        drawRoundRect(
            color = Color.White.copy(alpha = if (estado == EstadoGiroflex.DESLIGADO) 0.18f else 0.38f),
            topLeft = Offset(32.5f * u, 40f * u),
            size = Size(4.5f * u, 28f * u),
            cornerRadius = CornerRadius(2.25f * u)
        )

        // Base
        val corBase = if (fundoEscuro) Color(0xFFEEF2F8) else Color(0xFF2B3A57)
        drawRoundRect(
            color = corBase,
            topLeft = Offset(20f * u, 74f * u),
            size = Size(60f * u, 12f * u),
            cornerRadius = CornerRadius(4f * u)
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(20f * u, 74f * u),
            size = Size(60f * u, 3f * u),
            cornerRadius = CornerRadius(1.5f * u)
        )
    }
}

private fun DrawScope.brilho(centro: Offset, raio: Float, cor: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(cor, cor.copy(alpha = 0f)), center = centro, radius = raio),
        radius = raio,
        center = centro
    )
}

/** Dois feixes opostos girando, como o espelho dentro de um giroflex. */
private fun DrawScope.feixes(centro: Offset, raio: Float, angulo: Float, cor: Color) {
    val pincel = Brush.radialGradient(listOf(cor, cor.copy(alpha = 0f)), center = centro, radius = raio)
    rotate(angulo, pivot = centro) {
        for (base in floatArrayOf(-25f, 155f)) {
            drawArc(
                brush = pincel,
                startAngle = base,
                sweepAngle = 50f,
                useCenter = true,
                topLeft = Offset(centro.x - raio, centro.y - raio),
                size = Size(raio * 2, raio * 2)
            )
        }
    }
}
