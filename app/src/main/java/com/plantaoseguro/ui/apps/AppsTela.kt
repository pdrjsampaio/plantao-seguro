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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.TituloSecao

@Immutable
data class ItemApp(val pacote: String, val nome: String, val icone: ImageBitmap?)

@Composable
fun AppsTela(
    sugeridos: List<ItemApp>,
    outros: List<ItemApp>?,
    escolhidos: Set<String>,
    onAlternar: (String, Boolean) -> Unit,
    onMostrarOutros: () -> Unit,
    onVoltar: () -> Unit
) {
    var busca by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { BarraTopo("Apps que disparam a sirene", onVoltar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Durante o plantão, a sirene toca quando um destes apps mostrar uma notificação. " +
                        "O texto só é conferido, sem ser guardado, se a regra tiver filtro de contato ou palavra.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                TituloSecao("Apps de mensagem neste celular")
                if (sugeridos.isEmpty()) {
                    Text(
                        "Nenhum app de mensagem conhecido foi encontrado. Escolha em Outros apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                } else {
                    Bloco {
                        sugeridos.forEachIndexed { i, app ->
                            if (i > 0) Divisoria()
                            LinhaApp(app, app.pacote in escolhidos) { onAlternar(app.pacote, it) }
                        }
                    }
                }
            }
            item { TituloSecao("Outros apps") }
            if (outros == null) {
                item {
                    OutlinedButton(onClick = onMostrarOutros, modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text("Mostrar todos os apps")
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Buscar app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                val filtrados = outros.filter { busca.isBlank() || it.nome.contains(busca.trim(), ignoreCase = true) }
                items(filtrados, key = { it.pacote }) { app ->
                    LinhaApp(app, app.pacote in escolhidos, Modifier.padding(horizontal = 4.dp)) { onAlternar(app.pacote, it) }
                }
            }
        }
    }
}

@Composable
private fun LinhaApp(app: ItemApp, marcado: Boolean, modifier: Modifier = Modifier, onMudar: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = marcado, role = Role.Checkbox, onValueChange = onMudar)
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
        Checkbox(checked = marcado, onCheckedChange = null)
    }
}
