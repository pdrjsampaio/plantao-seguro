package com.plantaoseguro.servico

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.ui.tema.PlantaoTema
import kotlinx.coroutines.launch

/**
 * Pergunta aberta pela notificação "Volume baixo durante o plantão", com as
 * mesmas opções da versão anterior (30 min, 1 h, 2 h, até o fim) e "voltar agora".
 */
class EscolhaVolumeActivity : ComponentActivity() {

    private val grafo get() = (application as PlantaoApp).grafo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val minimo = intent.getIntExtra(EXTRA_MINIMO, 70)
        setContent {
            PlantaoTema {
                Dialog(onDismissRequest = { finish() }) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(24.dp)) {
                            Text("Volume baixo durante o plantão", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Por quanto tempo manter assim? Depois o volume de toque e de notificação volta para $minimo%.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            for ((rotulo, minutos) in OPCOES) {
                                TextButton(onClick = { manter(minutos) }, modifier = Modifier.fillMaxWidth()) { Text(rotulo) }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { voltarAgora() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Voltar o volume agora")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun manter(minutos: Int?) {
        grafo.escopo.launch { grafo.coordenador.manterVolumeBaixo(minutos) }
        finish()
    }

    private fun voltarAgora() {
        grafo.escopo.launch { grafo.coordenador.voltarVolume() }
        finish()
    }

    companion object {
        const val EXTRA_MINIMO = "minimo"
        private val OPCOES = listOf("30 minutos" to 30, "1 hora" to 60, "2 horas" to 120, "Até o fim do plantão" to null)
    }
}
