package com.plantaoseguro.ui.diagnostico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.sistema.Correcao
import com.plantaoseguro.sistema.Gravidade
import com.plantaoseguro.sistema.Verificacao
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.IconeRedondo
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.Extras

/** Resultado do teste ponta a ponta da escuta. */
@Immutable
sealed interface TesteEscuta {
    data object NaoFeito : TesteEscuta
    data object Rodando : TesteEscuta
    data class Ok(val ms: Long) : TesteEscuta
    data object Falhou : TesteEscuta
}

class AcoesDiagnostico(
    val corrigir: (Correcao) -> Unit = {},
    val testarEscuta: () -> Unit = {},
    val testarAlerta: () -> Unit = {},
    val testarBloqueado: () -> Unit = {},
    val voltar: () -> Unit = {}
)

@Composable
fun DiagnosticoTela(itens: List<Verificacao>, teste: TesteEscuta, acoes: AcoesDiagnostico) {
    val erros = itens.count { it.gravidade == Gravidade.ERRO }
    val avisos = itens.count { it.gravidade == Gravidade.AVISO }
    Scaffold(
        topBar = { BarraTopo("Diagnóstico", acoes.voltar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    when {
                        erros > 0 -> if (erros == 1) "1 problema impede o alerta" else "$erros problemas impedem o alerta"
                        avisos > 0 -> "Funcionando, com $avisos ponto(s) de atenção"
                        else -> "Tudo funcionando"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Text(
                    "O Android não deixa nenhum app garantir 100% de funcionamento em todos os celulares. Esta tela mostra o que dá para conferir e um teste de verdade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            item {
                TituloSecao("Teste de ponta a ponta")
                Bloco {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "O app publica uma notificação de teste e confere se a escuta percebe. É a prova de que as mensagens vão chegar até ele.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        when (teste) {
                            TesteEscuta.NaoFeito -> Unit
                            TesteEscuta.Rodando -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.padding(end = 10.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                                Text("Testando…", style = MaterialTheme.typography.bodyLarge)
                            }
                            is TesteEscuta.Ok -> Text(
                                "A escuta percebeu a notificação em ${teste.ms} ms.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Extras.cores.ok
                            )
                            TesteEscuta.Falhou -> Text(
                                "A escuta não percebeu a notificação. Desligue e ligue o acesso às notificações do Plantão Seguro e teste de novo.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = acoes.testarEscuta, enabled = teste != TesteEscuta.Rodando) { Text("Testar a escuta") }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = acoes.testarAlerta) { Text("Tocar o alerta") }
                            OutlinedButton(onClick = acoes.testarBloqueado) { Text("Testar bloqueado") }
                        }
                        Text(
                            "Testar bloqueado: toque, bloqueie o celular e espere 10 segundos. O alerta deve tocar e acender a tela.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            item {
                TituloSecao("Verificações")
                Bloco {
                    itens.forEachIndexed { i, v ->
                        if (i > 0) Divisoria()
                        LinhaVerificacao(v) { acoes.corrigir(v.correcao) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaVerificacao(v: Verificacao, onCorrigir: () -> Unit) {
    val extras = Extras.cores
    val (icone, cor) = when (v.gravidade) {
        Gravidade.OK -> Icons.Rounded.CheckCircle to extras.ok
        Gravidade.AVISO -> Icons.Rounded.Warning to extras.aviso
        Gravidade.ERRO -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
        Gravidade.INFO -> Icons.Rounded.Info to MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeRedondo(icone, cor, cor.copy(alpha = 0.13f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(v.detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (v.correcao != Correcao.NENHUMA && v.gravidade != Gravidade.OK) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onCorrigir) {
                Text(if (v.correcao == Correcao.RELIGAR_ESCUTA) "Religar" else "Resolver")
            }
        }
    }
}
