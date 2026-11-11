package com.plantaoseguro.ui.som

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.ModoDuracao
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.LinhaAjuste
import com.plantaoseguro.ui.comum.LinhaComConteudo
import com.plantaoseguro.ui.comum.LinhaInterruptor
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.util.Formato
import kotlin.math.roundToInt

/** "Sirene clássica, 100%, por 1 min" — para listas e resumos. */
fun resumoToque(t: Toque): String {
    val som = if (t.tocarSom) "${Catalogo.nome(t.som)}, ${t.volume}%" else "Só vibração"
    return "$som, ${duracaoToque(t)}"
}

fun duracaoToque(t: Toque): String = when (t.modoDuracao) {
    ModoDuracao.TEMPO -> "por " + Formato.duracao(t.duracaoSeg * 1000L)
    ModoDuracao.REPETICOES -> if (t.repeticoes == 1) "1 vez" else "${t.repeticoes} vezes"
    ModoDuracao.ATE_PARAR -> "até você parar"
}

class AcoesToque(
    val mudar: ((Toque) -> Toque) -> Unit = {},
    val escolherSom: () -> Unit = {},
    val previaVibracao: (Vibracao) -> Unit = {}
)

/**
 * Todas as opções de como o alerta toca, para usar dentro de um Bloco.
 * Serve ao som padrão do app e às regras com som próprio.
 */
@Composable
fun ColumnScope.OpcoesToque(t: Toque, acoes: AcoesToque, mostrarEscolhaDoSom: Boolean = true) {
    LinhaInterruptor(
        Icons.Rounded.MusicNote, "Tocar som",
        if (t.tocarSom) Catalogo.nome(t.som) else "Desligado: só vibra",
        t.tocarSom, { v -> acoes.mudar { it.copy(tocarSom = v) } }, corIcone = Extras.cores.sinal
    )
    if (t.tocarSom) {
        if (mostrarEscolhaDoSom) {
            LinhaAjuste(Icons.Rounded.MusicNote, "Escolher som", Catalogo.nome(t.som), onClick = acoes.escolherSom)
        }
        Divisoria()
        Volume(t.volume) { v -> acoes.mudar { it.copy(volume = v) } }
        LinhaInterruptor(
            Icons.AutoMirrored.Rounded.TrendingUp, "Volume crescente", "Começa baixo e chega ao volume escolhido em 20 segundos.",
            t.crescente, { v -> acoes.mudar { it.copy(crescente = v) } }
        )
    }
    Divisoria()
    LinhaComConteudo(Icons.Rounded.Vibration, "Vibração", "Toque para sentir o padrão.") {
        Fichas(
            Vibracao.entries.map { it to it.rotulo },
            t.vibracao,
            { v -> acoes.mudar { it.copy(vibracao = v) }; acoes.previaVibracao(v) }
        )
    }
    Divisoria()
    LinhaInterruptor(
        Icons.Rounded.FlashlightOn, "Piscar a lanterna", "Ajuda em lugar barulhento.",
        t.lanterna, { v -> acoes.mudar { it.copy(lanterna = v) } }
    )
    Divisoria()
    LinhaComConteudo(Icons.Rounded.Timer, "Por quanto tempo", null) {
        Fichas(
            listOf(ModoDuracao.TEMPO to "Por tempo", ModoDuracao.REPETICOES to "Repetições", ModoDuracao.ATE_PARAR to "Até eu parar"),
            t.modoDuracao,
            { v -> acoes.mudar { it.copy(modoDuracao = v) } }
        )
        Spacer(Modifier.height(8.dp))
        when (t.modoDuracao) {
            ModoDuracao.TEMPO -> Fichas(
                listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 600 to "10 min"),
                t.duracaoSeg,
                { v -> acoes.mudar { it.copy(duracaoSeg = v) } }
            )
            ModoDuracao.REPETICOES -> Fichas(
                listOf(1 to "1 vez", 3 to "3 vezes", 5 to "5 vezes", 10 to "10 vezes"),
                t.repeticoes,
                { v -> acoes.mudar { it.copy(repeticoes = v) } }
            )
            ModoDuracao.ATE_PARAR -> Text(
                "Toca até você parar ou abrir a mensagem (no máximo 20 minutos, para não gastar a bateria se ninguém ouvir).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divisoria()
    LinhaComConteudo(Icons.Rounded.Repeat, "Tocar de novo se ninguém parar", "Enquanto a mensagem continuar sem ser aberta.") {
        Fichas(
            listOf(0 to "Não", 2 to "2 min", 5 to "5 min", 10 to "10 min", 15 to "15 min"),
            t.insistirMin,
            { v -> acoes.mudar { it.copy(insistirMin = v) } }
        )
    }
}

@Composable
private fun Volume(volume: Int, onMudar: (Int) -> Unit) {
    var valor by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    LinhaComConteudo(
        Icons.AutoMirrored.Rounded.VolumeUp,
        "Volume do alerta: ${valor.roundToInt()}%",
        "Usa o volume de alarme: toca mesmo no silencioso."
    ) {
        Slider(
            value = valor,
            onValueChange = { valor = it },
            onValueChangeFinished = { onMudar(valor.roundToInt()) },
            valueRange = 10f..100f,
            steps = 8
        )
    }
}
