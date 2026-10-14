package com.plantaoseguro.ui.sons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.sirene.CategoriaSom
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.LinhaAjuste
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.Extras
import kotlin.math.roundToInt

class AcoesSons(
    val escolher: (SomRef) -> Unit = {},
    val previa: (SomRef) -> Unit = {},
    val pararPrevia: () -> Unit = {},
    val volume: (Int) -> Unit = {},
    val arquivo: () -> Unit = {},
    val usar: () -> Unit = {},
    val voltar: () -> Unit = {}
)

/**
 * Biblioteca de sons. Tocar numa linha escolhe e já toca uma prévia de alguns
 * segundos no volume real do alerta.
 */
@Composable
fun SonsTela(
    selecionado: SomRef,
    previa: SomRef?,
    volume: Int,
    doCelular: List<SomRef.Sistema>,
    acoes: AcoesSons
) {
    Scaffold(
        topBar = { BarraTopo("Som do alerta", acoes.voltar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = acoes.usar,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp)
                ) { Text("Usar ${Catalogo.nome(selecionado)}") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Volume(volume, acoes.volume) }
            for (cat in CategoriaSom.entries) {
                item {
                    TituloSecao(cat.titulo)
                    Bloco {
                        Catalogo.sons.filter { it.categoria == cat }.forEachIndexed { i, s ->
                            if (i > 0) Divisoria()
                            val ref = SomRef.Embutido(s.id)
                            LinhaSom(
                                nome = s.nome,
                                descricao = s.descricao,
                                selecionado = selecionado == ref,
                                tocando = previa == ref,
                                onSelecionar = { acoes.escolher(ref); acoes.previa(ref) },
                                onTocar = { if (previa == ref) acoes.pararPrevia() else acoes.previa(ref) }
                            )
                        }
                    }
                }
            }
            item {
                TituloSecao("Do seu celular")
                Bloco {
                    val arquivo = selecionado as? SomRef.Arquivo
                    if (arquivo != null) {
                        LinhaSom(
                            nome = arquivo.nome,
                            descricao = "Arquivo escolhido por você",
                            selecionado = true,
                            tocando = previa == arquivo,
                            onSelecionar = { acoes.previa(arquivo) },
                            onTocar = { if (previa == arquivo) acoes.pararPrevia() else acoes.previa(arquivo) }
                        )
                        Divisoria()
                    }
                    doCelular.forEach { s ->
                        LinhaSom(
                            nome = s.nome,
                            descricao = "Alarme do celular",
                            selecionado = selecionado == s,
                            tocando = previa == s,
                            onSelecionar = { acoes.escolher(s); acoes.previa(s) },
                            onTocar = { if (previa == s) acoes.pararPrevia() else acoes.previa(s) }
                        )
                        Divisoria()
                    }
                    LinhaAjuste(
                        Icons.Rounded.FolderOpen,
                        "Importar um arquivo de áudio",
                        "MP3, OGG, WAV ou outro som salvo no celular.",
                        onClick = acoes.arquivo
                    )
                }
            }
        }
    }
}

@Composable
private fun Volume(volume: Int, onVolume: (Int) -> Unit) {
    var valor by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volume do alerta", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${valor.roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = valor,
            onValueChange = { valor = it },
            onValueChangeFinished = { onVolume(valor.roundToInt()) },
            valueRange = 10f..100f,
            steps = 8
        )
        Text(
            "A prévia toca neste volume, no canal de alarme, como o alerta de verdade.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LinhaSom(
    nome: String,
    descricao: String,
    selecionado: Boolean,
    tocando: Boolean,
    onSelecionar: () -> Unit,
    onTocar: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selecionado, role = Role.RadioButton, onClick = onSelecionar)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selecionado, onClick = null, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onTocar != null) {
            IconButton(onClick = onTocar) {
                val cor = if (tocando) Extras.cores.sinal else MaterialTheme.colorScheme.primary
                Box(
                    Modifier.size(36.dp).background(cor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (tocando) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (tocando) "Parar prévia de $nome" else "Ouvir $nome",
                        tint = cor
                    )
                }
            }
        }
    }
}
