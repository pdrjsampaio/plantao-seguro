package com.plantaoseguro.ui.permissoes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.sistema.Dica
import com.plantaoseguro.sistema.Fabricante
import com.plantaoseguro.sistema.Requisito
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.IconeRedondo
import com.plantaoseguro.ui.comum.LinhaAjuste
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.Extras

fun iconeDe(r: Requisito): ImageVector = when (r) {
    Requisito.NOTIFICACOES -> Icons.Rounded.Notifications
    Requisito.ESCUTA -> Icons.Rounded.Visibility
    Requisito.NAO_PERTURBE -> Icons.Rounded.DoNotDisturbOn
    Requisito.TELA_CHEIA -> Icons.Rounded.Fullscreen
    Requisito.BATERIA -> Icons.Rounded.BatteryChargingFull
    Requisito.ALARMES -> Icons.Rounded.Alarm
}

@Composable
fun PermissoesTela(
    status: List<Pair<Requisito, Boolean>>,
    fabricante: Fabricante,
    dicas: List<Dica>,
    onPermitir: (Requisito) -> Unit,
    onDica: (Dica) -> Unit,
    onTestar: () -> Unit,
    onTestarBloqueado: () -> Unit,
    onVoltar: () -> Unit
) {
    val faltam = status.count { !it.second }
    Scaffold(
        topBar = { BarraTopo("Permissões e bateria", onVoltar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (faltam == 0) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Extras.cores.ok, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Tudo liberado", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Text(
                            if (faltam == 1) "Falta liberar 1 item" else "Faltam liberar $faltam itens",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                Text(
                    "Toque em Permitir, ligue a opção do Plantão Seguro na tela que abrir e volte para cá.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            item {
                TituloSecao("Do Android")
                Bloco {
                    status.forEachIndexed { i, (r, ok) ->
                        if (i > 0) Divisoria()
                        LinhaRequisito(r, ok) { onPermitir(r) }
                    }
                }
            }
            if (dicas.isNotEmpty()) {
                item {
                    TituloSecao("No celular ${fabricante.nome}")
                    Text(
                        "O Android não deixa o app conferir estes ajustes. Faça uma vez e pronto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    )
                    Bloco {
                        dicas.forEachIndexed { i, d ->
                            if (i > 0) Divisoria()
                            LinhaDica(d) { onDica(d) }
                        }
                    }
                }
            }
            item {
                TituloSecao("Teste completo")
                Bloco {
                    LinhaAjuste(
                        Icons.Rounded.NotificationsActive,
                        "Testar agora",
                        "Toca a sirene por 8 segundos.",
                        corIcone = Extras.cores.sinal,
                        onClick = onTestar
                    )
                    Divisoria()
                    LinhaAjuste(
                        Icons.Rounded.Lock,
                        "Testar com a tela bloqueada",
                        "Toque aqui, bloqueie o celular e espere 10 segundos. A sirene deve tocar e acender a tela.",
                        corIcone = Extras.cores.sinal,
                        onClick = onTestarBloqueado
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaRequisito(r: Requisito, ok: Boolean, onPermitir: () -> Unit) {
    val cor = if (ok) Extras.cores.ok else Extras.cores.aviso
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeRedondo(iconeDe(r), cor, cor.copy(alpha = 0.13f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(r.porque, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (ok) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Liberado", tint = Extras.cores.ok)
        } else {
            FilledTonalButton(onClick = onPermitir) { Text("Permitir") }
        }
    }
}

@Composable
private fun LinhaDica(d: Dica, onAbrir: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeRedondo(Icons.Rounded.PhoneAndroid, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(d.passos, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (d.atalhos.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = onAbrir) { Text("Abrir") }
        }
    }
}
