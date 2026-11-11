package com.plantaoseguro.ui.regras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.IconeRedondo
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.som.CartaoSom
import com.plantaoseguro.ui.som.resumoToque
import com.plantaoseguro.util.Formato

@Immutable
data class ResumoRegra(
    val regra: Regra,
    val apps: String,
    val filtro: String,
    val toque: String,
    val silenciada: String?
)

fun resumir(r: Regra, nomeDoApp: (String) -> String, agora: Long, padrao: Toque = Toque()): ResumoRegra {
    val apps = r.apps.map(nomeDoApp).sorted().let {
        when (it.size) {
            0 -> "Nenhum app escolhido"
            1, 2 -> it.joinToString(" e ")
            else -> "${it[0]}, ${it[1]} e mais ${it.size - 2}"
        }
    }
    val partes = buildList {
        if (r.contatos.isNotEmpty()) add("de " + r.contatos.joinToString(", "))
        if (r.palavras.isNotEmpty()) add("com " + r.palavras.joinToString(", "))
    }
    val filtro = (if (partes.isEmpty()) "Qualquer mensagem" else "Só mensagens " + partes.joinToString(" e ")) +
        if (r.quando == QuandoVale.SEMPRE) ", a qualquer hora" else ", no plantão"
    val toque = if (r.somProprio) {
        "Som próprio: " + resumoToque(r.toque)
    } else {
        "Som padrão (" + (if (padrao.tocarSom) Catalogo.nome(padrao.som) else "só vibração") + ")"
    }
    return ResumoRegra(
        regra = r,
        apps = apps,
        filtro = filtro,
        toque = toque,
        silenciada = if (r.silenciada(agora)) "Silenciada até ${Formato.quando(r.silenciadaAte, agora)}" else null
    )
}

@Composable
fun RegrasTela(
    regras: List<ResumoRegra>,
    onNova: () -> Unit,
    onEditar: (String) -> Unit,
    onLigar: (String, Boolean) -> Unit,
    somPadrao: Toque = Toque(),
    ouvindo: Boolean = false,
    onOuvirPadrao: () -> Unit = {},
    onSomPadrao: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Alertas",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)
                )
            }
            item {
                TituloSecao("Som padrão")
                CartaoSom(somPadrao, ouvindo, onOuvir = onOuvirPadrao, onTrocar = onSomPadrao, rotuloTrocar = "Mudar o som")
                Text(
                    "Vale para todas as regras, menos as que têm som próprio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
                )
            }
            item {
                TituloSecao("Regras")
                Text(
                    "Cada regra diz o que dispara o alerta. São conferidas de cima para baixo; as de prioridade máxima primeiro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column {
                        if (regras.isEmpty()) {
                            Text(
                                "Nenhuma regra. Sem regras, nada toca. Crie uma para começar.",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                        regras.forEachIndexed { i, r ->
                            if (i > 0) Divisoria()
                            LinhaRegra(r, onEditar, onLigar)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNova,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Nova regra") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).semantics { contentDescription = "Nova regra" }
        )
    }
}

@Composable
private fun LinhaRegra(r: ResumoRegra, onEditar: (String) -> Unit, onLigar: (String, Boolean) -> Unit) {
    val ligada = r.regra.ligada
    val cor = when {
        !ligada -> MaterialTheme.colorScheme.onSurfaceVariant
        r.silenciada != null -> com.plantaoseguro.ui.tema.Extras.cores.aviso
        else -> com.plantaoseguro.ui.tema.Extras.cores.sinal
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onEditar(r.regra.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeRedondo(
            if (r.silenciada != null) Icons.Rounded.NotificationsPaused else Icons.Rounded.NotificationsActive,
            cor, cor.copy(alpha = 0.13f)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.regra.nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (r.regra.critica) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Prioridade máxima",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(r.apps, style = MaterialTheme.typography.bodyMedium)
            Text(r.filtro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(r.toque, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.silenciada != null) {
                Text(r.silenciada, style = MaterialTheme.typography.labelMedium, color = com.plantaoseguro.ui.tema.Extras.cores.aviso)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = ligada,
            onCheckedChange = { onLigar(r.regra.id, it) },
            modifier = Modifier.semantics { contentDescription = "Ligar a regra ${r.regra.nome}" }
        )
    }
}
