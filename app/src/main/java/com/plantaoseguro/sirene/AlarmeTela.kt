package com.plantaoseguro.sirene

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.ui.comum.EstadoGiroflex
import com.plantaoseguro.ui.comum.Giroflex
import com.plantaoseguro.ui.tema.BarlowCondensada
import com.plantaoseguro.util.Formato

/**
 * Tela do alerta, feita para ser usada meio dormindo: um botão enorme para
 * parar e um caminho direto para a conversa.
 */
@Composable
fun AlarmeTela(
    alerta: AlertaAtivo,
    mostrarConversa: Boolean,
    volumePara: Boolean,
    onParar: () -> Unit,
    onAbrir: (() -> Unit)?,
    onSilenciar: (() -> Unit)?,
    animar: Boolean = true
) {
    val transicao = rememberInfiniteTransition(label = "alarme")
    val pulso by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulso"
    )
    val p = if (animar) pulso else 0.5f
    val topo = lerp(Color(0xFF5C0A10), Color(0xFF9E1219), p)
    val titulo = when {
        alerta.teste -> "Teste do alerta"
        alerta.chamada -> "Chamada de trabalho"
        alerta.quantidade > 1 -> "${alerta.quantidade} mensagens"
        else -> "Mensagem de trabalho"
    }
    val linha = when {
        alerta.teste -> "Se você está ouvindo, o alerta funciona."
        alerta.appNome != null -> "No ${alerta.appNome}"
        else -> "Chegou uma notificação"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topo, Color(0xFF1A0608))))
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Giroflex(EstadoGiroflex.TOCANDO, tamanho = 156.dp, animar = animar)
                Spacer(Modifier.height(18.dp))
                Text(titulo, style = MaterialTheme.typography.displayMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(linha, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
                if (mostrarConversa && alerta.conversa != null) {
                    Text(alerta.conversa, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFD6D8), textAlign = TextAlign.Center)
                }
                if (!alerta.teste) {
                    Text(
                        buildString {
                            append("Regra ").append(alerta.regraNome)
                            alerta.plantaoNome?.let { append(", ").append(it) }
                            append(", desde ").append(Formato.hora(alerta.inicio))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF2C7C9),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onParar,
                    modifier = Modifier.size(188.dp).semantics { contentDescription = "Parar o alerta" },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3161E))
                ) {
                    Text("Parar", fontFamily = BarlowCondensada, fontWeight = FontWeight.Bold, fontSize = 46.sp)
                }
                Spacer(Modifier.height(18.dp))
                if (onAbrir != null) {
                    OutlinedButton(
                        onClick = onAbrir,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                    ) {
                        Text("Parar e abrir a conversa", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (onSilenciar != null) {
                    TextButton(onClick = onSilenciar, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Silenciar esta regra por 1 hora", color = Color(0xFFF2C7C9))
                    }
                }
                if (volumePara) {
                    Text(
                        "Os botões de volume também param o alerta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE3A5A9),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
