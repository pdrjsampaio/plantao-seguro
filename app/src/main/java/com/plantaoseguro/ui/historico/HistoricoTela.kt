package com.plantaoseguro.ui.historico

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.plantaoseguro.modelo.Evento
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.nucleo.Texto
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.IconeRedondo
import com.plantaoseguro.ui.tema.EstiloHorario
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.util.Formato

enum class FiltroHistorico(val rotulo: String) { TUDO("Tudo"), ALERTAS("Alertas"), PLANTOES("Plantões"), PROBLEMAS("Problemas") }

private fun FiltroHistorico.aceita(e: Evento): Boolean = when (this) {
    FiltroHistorico.TUDO -> true
    FiltroHistorico.ALERTAS -> e.tipo in setOf(TipoEvento.ALERTA, TipoEvento.ALERTA_CONFIRMADO, TipoEvento.ALERTA_NAO_CONFIRMADO, TipoEvento.TESTE)
    FiltroHistorico.PLANTOES -> e.tipo == TipoEvento.PLANTAO_INICIO || e.tipo == TipoEvento.PLANTAO_FIM
    FiltroHistorico.PROBLEMAS -> e.nivel == Nivel.ERRO || e.nivel == Nivel.AVISO
}

enum class FormatoExportacao { TEXTO, CSV }

@Composable
fun HistoricoTela(eventos: List<Evento>, agora: Long, onExportar: (FormatoExportacao) -> Unit, onLimpar: () -> Unit) {
    var filtro by rememberSaveable { mutableStateOf(FiltroHistorico.TUDO) }
    var busca by rememberSaveable { mutableStateOf("") }
    var confirmarLimpeza by remember { mutableStateOf(false) }
    var menuExportar by remember { mutableStateOf(false) }
    val termo = Texto.normalizar(busca)
    val visiveis = eventos.filter {
        filtro.aceita(it) && (termo.isEmpty() || Texto.normalizar(it.titulo + " " + (it.detalhe ?: "")).contains(termo))
    }
    val porDia = visiveis.groupBy { Formato.cabecalhoDia(it.t, agora) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Histórico", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuExportar = true }, enabled = eventos.isNotEmpty()) {
                        Icon(Icons.Rounded.Share, contentDescription = "Exportar histórico")
                    }
                    DropdownMenu(expanded = menuExportar, onDismissRequest = { menuExportar = false }) {
                        DropdownMenuItem(text = { Text("Exportar como texto") }, onClick = { menuExportar = false; onExportar(FormatoExportacao.TEXTO) })
                        DropdownMenuItem(text = { Text("Exportar como planilha (CSV)") }, onClick = { menuExportar = false; onExportar(FormatoExportacao.CSV) })
                    }
                }
                IconButton(onClick = { confirmarLimpeza = true }, enabled = eventos.isNotEmpty()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Apagar histórico")
                }
            }
        }
        item {
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Buscar no histórico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item {
            Fichas(
                FiltroHistorico.entries.map { it to it.rotulo },
                filtro,
                { filtro = it },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (visiveis.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        if (eventos.isEmpty()) "Nada por aqui ainda" else "Nada encontrado",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Aqui aparece cada plantão que começou ou terminou e cada alerta, com quem parou e como. O texto das mensagens nunca é guardado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        porDia.forEach { (dia, lista) ->
            item(key = "dia-$dia") {
                Text(
                    dia,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
                )
            }
            items(lista) { e -> LinhaEvento(e) }
        }
    }

    if (confirmarLimpeza) {
        AlertDialog(
            onDismissRequest = { confirmarLimpeza = false },
            title = { Text("Apagar o histórico?") },
            text = { Text("Os registros somem deste celular. Plantões, regras e ajustes continuam.") },
            confirmButton = {
                TextButton(onClick = { confirmarLimpeza = false; onLimpar() }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarLimpeza = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun LinhaEvento(e: Evento) {
    val extras = Extras.cores
    val (icone, cor) = visual(e, extras.sinal, extras.ok, extras.aviso, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.primary)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconeRedondo(icone, cor, cor.copy(alpha = 0.13f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (e.detalhe != null) {
                Text(e.detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            Formato.hora(e.t),
            style = EstiloHorario.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun visual(e: Evento, sinal: Color, ok: Color, aviso: Color, erro: Color, neutro: Color): Pair<ImageVector, Color> = when {
    e.nivel == Nivel.ERRO -> Icons.Rounded.ErrorOutline to erro
    e.tipo == TipoEvento.ALERTA -> Icons.Rounded.NotificationsActive to sinal
    e.tipo == TipoEvento.ALERTA_CONFIRMADO -> Icons.Rounded.CheckCircle to ok
    e.tipo == TipoEvento.ALERTA_NAO_CONFIRMADO -> Icons.Rounded.NotificationImportant to aviso
    e.tipo == TipoEvento.TESTE -> Icons.Rounded.Science to ok
    e.tipo == TipoEvento.PLANTAO_INICIO -> Icons.Rounded.Event to (if (e.nivel == Nivel.AVISO) aviso else ok)
    e.tipo == TipoEvento.PLANTAO_FIM -> Icons.Rounded.EventBusy to neutro
    e.nivel == Nivel.AVISO -> Icons.Rounded.ErrorOutline to aviso
    else -> Icons.Rounded.Info to neutro
}
