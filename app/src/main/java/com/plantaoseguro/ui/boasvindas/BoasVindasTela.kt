package com.plantaoseguro.ui.boasvindas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.sistema.Dica
import com.plantaoseguro.sistema.Fabricante
import com.plantaoseguro.sistema.Requisito
import com.plantaoseguro.ui.apps.ItemApp
import com.plantaoseguro.ui.comum.EstadoGiroflex
import com.plantaoseguro.ui.comum.Giroflex
import com.plantaoseguro.ui.permissoes.iconeDe
import com.plantaoseguro.ui.comum.IconeRedondo
import com.plantaoseguro.ui.tema.Extras

const val PASSOS_BOAS_VINDAS = 5

enum class DepoisDasBoasVindas { NOVO_PLANTAO, COMECAR_AGORA, INICIO }

/** Sons oferecidos na primeira utilização (os outros ficam na biblioteca). */
val SONS_INICIAIS = listOf("sirene_classica", "sirene_rapida", "alarme_incendio", "chamada_enfermagem", "bipe_pager")

class AcoesBoasVindas(
    val alternarApp: (String, Boolean) -> Unit = { _, _ -> },
    val permitir: (Requisito) -> Unit = {},
    val dica: (Dica) -> Unit = {},
    val escolherSom: (SomRef) -> Unit = {},
    val testar: () -> Unit = {},
    val testarBloqueado: () -> Unit = {},
    val avancar: () -> Unit = {},
    val voltar: () -> Unit = {},
    val concluir: (DepoisDasBoasVindas) -> Unit = {}
)

@Composable
fun BoasVindasTela(
    passo: Int,
    apps: List<ItemApp>,
    appsEscolhidos: Set<String>,
    permissoes: List<Pair<Requisito, Boolean>>,
    fabricante: Fabricante,
    dicas: List<Dica>,
    som: SomRef,
    acoes: AcoesBoasVindas,
    animar: Boolean = true
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Progresso(passo)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            when (passo) {
                0 -> PassoIntro(animar)
                1 -> PassoApps(apps, appsEscolhidos, acoes)
                2 -> PassoPermissoes(permissoes, acoes)
                3 -> PassoFabricante(fabricante, dicas, acoes)
                else -> PassoSom(som, acoes)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (passo > 0) {
                TextButton(onClick = acoes.voltar) { Text("Voltar") }
            }
            Spacer(Modifier.weight(1f))
            if (passo < PASSOS_BOAS_VINDAS - 1) {
                Button(
                    onClick = acoes.avancar,
                    enabled = passo != 1 || appsEscolhidos.isNotEmpty(),
                    modifier = Modifier.height(52.dp)
                ) { Text(if (passo == 0) "Começar" else "Continuar") }
            }
        }
        if (passo == PASSOS_BOAS_VINDAS - 1) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { acoes.concluir(DepoisDasBoasVindas.NOVO_PLANTAO) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Cadastrar meu plantão")
                }
                OutlinedButton(onClick = { acoes.concluir(DepoisDasBoasVindas.COMECAR_AGORA) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Começar um plantão agora")
                }
                TextButton(onClick = { acoes.concluir(DepoisDasBoasVindas.INICIO) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ir para o início")
                }
            }
        }
    }
}

@Composable
private fun Progresso(passo: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
            .semantics { contentDescription = "Passo ${passo + 1} de $PASSOS_BOAS_VINDAS" },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until PASSOS_BOAS_VINDAS) {
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(
                        if (i <= passo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
private fun PassoIntro(animar: Boolean) {
    Surface(color = Extras.cores.palco, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
            Giroflex(EstadoGiroflex.ARMADO, tamanho = 140.dp, animar = animar)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Alerta forte para as mensagens do plantão", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "Durante o seu plantão, quando chegar mensagem de trabalho, o Plantão Seguro toca um alerta forte, mesmo com o celular no silencioso. Fora do plantão, ele fica quieto e o celular toca como sempre.",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(16.dp))
    Ponto("Só toca nos horários e apps que você escolher.")
    Ponto("Não lê suas mensagens, a menos que você crie um filtro por grupo ou palavra (e mesmo assim nada é gravado).")
    Ponto("Não usa internet e não tem conta: tudo fica no seu celular.")
}

@Composable
private fun Ponto(texto: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Extras.cores.ok, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PassoApps(apps: List<ItemApp>, escolhidos: Set<String>, acoes: AcoesBoasVindas) {
    Text("De onde vêm as mensagens de trabalho?", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Escolha os apps. Depois dá para criar regras mais finas, como \"só o grupo do plantão\".",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    if (apps.isEmpty()) {
        Text("Nenhum app de mensagem conhecido foi encontrado. Você pode escolher outros apps depois, em Alertas.", style = MaterialTheme.typography.bodyLarge)
    }
    for (app in apps) {
        val marcado = app.pacote in escolhidos
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = marcado, role = Role.Checkbox) { acoes.alternarApp(app.pacote, it) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icone != null) {
                Image(app.icone, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Text(app.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Checkbox(checked = marcado, onCheckedChange = null)
        }
    }
}

@Composable
private fun PassoPermissoes(permissoes: List<Pair<Requisito, Boolean>>, acoes: AcoesBoasVindas) {
    Text("Libere o que o alerta precisa", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Toque em Permitir, ligue a opção do Plantão Seguro na tela que abrir e volte. Cada item explica por que é necessário.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    for ((r, ok) in permissoes) {
        val cor = if (ok) Extras.cores.ok else Extras.cores.aviso
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeRedondo(iconeDe(r), cor, cor.copy(alpha = 0.13f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.titulo, style = MaterialTheme.typography.titleSmall)
                Text(r.porque, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (ok) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Liberado", tint = Extras.cores.ok)
            } else {
                FilledTonalButton(onClick = { acoes.permitir(r) }) { Text("Permitir") }
            }
        }
    }
}

@Composable
private fun PassoFabricante(f: Fabricante, dicas: List<Dica>, acoes: AcoesBoasVindas) {
    Text(if (f == Fabricante.OUTRO) "Um ajuste de bateria" else "Um ajuste do ${f.nome}", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Alguns celulares fecham apps parados para economizar bateria, mesmo com tudo liberado. O Android não deixa o app conferir estes ajustes, então faça uma vez:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    dicas.forEachIndexed { i, d ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("${i + 1}. ${d.titulo}", style = MaterialTheme.typography.titleSmall)
                Text(d.passos, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.atalhos.isNotEmpty()) {
                    OutlinedButton(onClick = { acoes.dica(d) }, modifier = Modifier.padding(top = 8.dp)) { Text("Abrir") }
                }
            }
        }
    }
}

@Composable
private fun PassoSom(som: SomRef, acoes: AcoesBoasVindas) {
    Text("Escolha o som e teste", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Tem mais de 20 sons na biblioteca, em Alertas. Comece com um destes:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    for (id in SONS_INICIAIS) {
        val s = Catalogo.porId(id) ?: continue
        val ref = SomRef.Embutido(id)
        Row(
            Modifier.fillMaxWidth()
                .selectable(selected = som == ref, role = Role.RadioButton) { acoes.escolherSom(ref) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = som == ref, onClick = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(s.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(s.descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = acoes.testar) { Text("Testar agora") }
        OutlinedButton(onClick = acoes.testarBloqueado) { Text("Testar bloqueado") }
    }
    Text(
        "Testar bloqueado: toque, bloqueie o celular e espere 10 segundos. O alerta deve tocar e acender a tela. Se não acender, volte ao passo das permissões.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}
