package com.plantaoseguro.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.nucleo.Texto
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.TituloSecao

/** Nomes que costumam aparecer em apps de ponto eletrônico. */
private val PISTAS_DE_PONTO = listOf(
    "ponto", "kairos", "ahgora", "tangerino", "pontomais", "pontotel", "secullum", "oitchau",
    "meu rh", "meurh", "rh", "factorial", "solides", "dimep", "jornada"
)

fun pareceAppDePonto(nome: String): Boolean {
    val n = Texto.normalizar(nome)
    return PISTAS_DE_PONTO.any { p -> Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(p) + "($|[^\\p{L}\\p{N}])").containsMatchIn(n) }
}

/**
 * Escolha do app usado para bater o ponto. O Plantão Seguro só abre esse app
 * (pelo lembrete ou pela tela inicial); quem bate o ponto é você.
 */
@Composable
fun AppPontoTela(todos: List<ItemApp>?, escolhido: String?, onEscolher: (String?) -> Unit, onVoltar: () -> Unit) {
    var busca by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = { BarraTopo("App do ponto", onVoltar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Escolha o app que você usa para bater o ponto. Nos plantões com lembrete de ponto, o aviso de entrada e de saída " +
                        "abre esse app quando você toca nele. Quem bate o ponto continua sendo você.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (escolhido != null) {
                    TextButton(onClick = { onEscolher(null) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Não usar app do ponto")
                    }
                }
            }
            if (todos == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                val sugeridos = todos.filter { pareceAppDePonto(it.nome) }
                if (sugeridos.isNotEmpty()) {
                    item {
                        TituloSecao("Parecem apps de ponto")
                        Bloco {
                            sugeridos.forEachIndexed { i, app ->
                                if (i > 0) Divisoria()
                                LinhaEscolha(app, app.pacote == escolhido) { onEscolher(app.pacote) }
                            }
                        }
                    }
                }
                item {
                    TituloSecao("Todos os apps")
                    OutlinedTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Buscar app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                val filtrados = todos.filter { busca.isBlank() || Texto.normalizar(it.nome).contains(Texto.normalizar(busca)) }
                items(filtrados, key = { it.pacote }) { app ->
                    LinhaEscolha(app, app.pacote == escolhido, Modifier.padding(horizontal = 4.dp)) { onEscolher(app.pacote) }
                }
            }
        }
    }
}

@Composable
private fun LinhaEscolha(app: ItemApp, marcado: Boolean, modifier: Modifier = Modifier, onEscolher: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = marcado, role = Role.RadioButton, onClick = onEscolher)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icone != null) {
            Image(app.icone, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Text(app.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        RadioButton(selected = marcado, onClick = null)
    }
}
