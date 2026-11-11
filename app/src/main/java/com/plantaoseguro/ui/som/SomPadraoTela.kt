package com.plantaoseguro.ui.som

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.BarlowCondensada
import com.plantaoseguro.ui.tema.Extras

class AcoesSomPadrao(
    val toque: AcoesToque = AcoesToque(),
    val ouvir: () -> Unit = {},
    val testar: () -> Unit = {},
    val voltar: () -> Unit = {}
)

/**
 * "Som do alerta": o som padrão do app, que vale para toda regra sem som
 * próprio. As mudanças valem na hora, sem botão de salvar.
 */
@Composable
fun SomPadraoTela(t: Toque, ouvindo: Boolean, regrasComPadrao: Int, regrasTotal: Int, acoes: AcoesSomPadrao) {
    Scaffold(
        topBar = { BarraTopo("Som do alerta", acoes.voltar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                OutlinedButton(
                    onClick = acoes.testar,
                    modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp)
                ) { Text("Testar o alerta completo") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            CartaoSom(t, ouvindo, onOuvir = acoes.ouvir, onTrocar = acoes.toque.escolherSom)
            Text(
                when {
                    regrasTotal == 0 -> "Este é o som padrão do app. Toda regra nova começa usando ele."
                    regrasComPadrao == regrasTotal -> "Este é o som padrão do app. Todas as suas regras usam ele."
                    regrasComPadrao == 0 -> "Este é o som padrão do app. Hoje todas as regras têm som próprio; regras novas começam com este."
                    else -> "Este é o som padrão do app. Vale para $regrasComPadrao de $regrasTotal regras; as outras têm som próprio."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            TituloSecao("Como toca")
            Bloco { OpcoesToque(t, acoes.toque, mostrarEscolhaDoSom = false) }
        }
    }
}

/** Mostra qual som está valendo, com ouvir e trocar. Usado aqui e no topo da aba Alertas. */
@Composable
fun CartaoSom(
    t: Toque,
    ouvindo: Boolean,
    onOuvir: () -> Unit,
    onTrocar: () -> Unit,
    modifier: Modifier = Modifier,
    rotuloTrocar: String = "Trocar som"
) {
    val cores = Extras.cores
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = cores.palco
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = cores.sinal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (t.tocarSom) Catalogo.origem(t.som) else "Sem som",
                    style = MaterialTheme.typography.labelLarge,
                    color = cores.noPalcoSuave
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (t.tocarSom) Catalogo.nome(t.som) else "Só vibração",
                fontFamily = BarlowCondensada,
                style = MaterialTheme.typography.headlineLarge,
                color = cores.noPalco
            )
            Text(
                (if (t.tocarSom) Catalogo.descricao(t.som) + " " else "") + "Volume ${t.volume}%, ${duracaoToque(t)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = cores.noPalcoSuave
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t.tocarSom) {
                    OutlinedButton(
                        onClick = onOuvir,
                        border = BorderStroke(1.dp, cores.noPalcoSuave),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cores.noPalco)
                    ) {
                        Icon(if (ouvindo) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (ouvindo) "Parar" else "Ouvir")
                    }
                }
                Button(
                    onClick = onTrocar,
                    colors = ButtonDefaults.buttonColors(containerColor = cores.sinal, contentColor = cores.noSinal)
                ) { Text(rotuloTrocar) }
            }
        }
    }
}
