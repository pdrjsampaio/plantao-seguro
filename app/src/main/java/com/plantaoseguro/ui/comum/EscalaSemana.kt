package com.plantaoseguro.ui.comum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantaoseguro.ui.tema.Extras

/** Um trecho de plantão dentro de um dia: frações de 0 (meia-noite) a 1. */
@Immutable
data class Trecho(val de: Float, val ate: Float, val cor: Color)

@Immutable
data class DiaEscala(val rotulo: String, val numero: String, val hoje: Boolean, val trechos: List<Trecho>, val agora: Float?)

/**
 * A escala dos próximos sete dias, como uma folha de plantão: cada coluna é
 * um dia (meia-noite em cima, meia-noite embaixo) com os plantões pintados.
 */
@Composable
fun EscalaSemana(dias: List<DiaEscala>, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    val extras = Extras.cores
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.width(26.dp).height(156.dp).padding(top = 36.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            for (h in listOf("0h", "12h", "24h")) {
                Text(h, style = MaterialTheme.typography.labelSmall, color = cores.onSurfaceVariant)
            }
        }
        for (d in dias) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.rotulo,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (d.hoje) FontWeight.Bold else FontWeight.Medium,
                    color = if (d.hoje) cores.onSurface else cores.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    d.numero,
                    style = MaterialTheme.typography.labelSmall,
                    color = cores.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                val trilho = cores.surfaceVariant
                val grade = extras.linha
                val contorno = cores.onSurface
                val marcador = extras.sinal
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val r = CornerRadius(8.dp.toPx())
                    drawRoundRect(trilho, cornerRadius = r)
                    for (f in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                        drawLine(grade, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1.dp.toPx())
                    }
                    for (t in d.trechos) {
                        val y0 = size.height * t.de.coerceIn(0f, 1f)
                        val y1 = size.height * t.ate.coerceIn(0f, 1f)
                        if (y1 - y0 < 1f) continue
                        drawRoundRect(
                            color = t.cor,
                            topLeft = Offset(3.dp.toPx(), y0),
                            size = Size(size.width - 6.dp.toPx(), y1 - y0),
                            cornerRadius = CornerRadius(5.dp.toPx())
                        )
                    }
                    if (d.hoje) {
                        drawRoundRect(contorno, cornerRadius = r, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    d.agora?.let { f ->
                        val y = size.height * f
                        drawLine(marcador, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx())
                        drawCircle(marcador, radius = 3.5.dp.toPx(), center = Offset(0f, y))
                    }
                }
            }
        }
    }
}
