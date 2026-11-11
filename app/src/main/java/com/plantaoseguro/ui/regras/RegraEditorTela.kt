package com.plantaoseguro.ui.regras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.LinhaAjuste
import com.plantaoseguro.ui.comum.LinhaComConteudo
import com.plantaoseguro.ui.comum.LinhaInterruptor
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.som.AcoesToque
import com.plantaoseguro.ui.som.OpcoesToque
import com.plantaoseguro.ui.som.resumoToque
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.util.Formato

class AcoesRegra(
    val mudar: ((Regra) -> Regra) -> Unit = {},
    val mudarToque: ((Toque) -> Toque) -> Unit = {},
    val usarSomProprio: (Boolean) -> Unit = {},
    val abrirSomPadrao: () -> Unit = {},
    val escolherApps: () -> Unit = {},
    val escolherSom: () -> Unit = {},
    val previaVibracao: (Vibracao) -> Unit = {},
    val testar: () -> Unit = {},
    val silenciar: (Int) -> Unit = {},
    val salvar: () -> Unit = {},
    val excluir: () -> Unit = {},
    val voltar: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegraEditorTela(
    r: Regra,
    nova: Boolean,
    erro: String?,
    nomesApps: List<String>,
    plantoes: List<Pair<String, String>>,
    agora: Long,
    acoes: AcoesRegra,
    padrao: Toque = Toque()
) {
    var confirmarExclusao by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BarraTopo(if (nova) "Nova regra" else "Editar regra", acoes.voltar) {
                if (!nova) {
                    IconButton(onClick = { confirmarExclusao = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Excluir regra")
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (erro != null) {
                        Text(erro, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = acoes.testar, modifier = Modifier.weight(1f).height(52.dp)) { Text("Testar") }
                        Button(onClick = acoes.salvar, modifier = Modifier.weight(2f).height(52.dp)) {
                            Text(if (nova) "Salvar regra" else "Salvar alterações")
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = r.nome,
                onValueChange = { v -> acoes.mudar { it.copy(nome = v.take(40)) } },
                label = { Text("Nome da regra") },
                placeholder = { Text("Ex.: Grupo do plantão") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )

            TituloSecao("De onde vem")
            Bloco {
                LinhaAjuste(
                    Icons.Rounded.Apps,
                    "Apps",
                    if (nomesApps.isEmpty()) "Nenhum escolhido" else nomesApps.joinToString(", "),
                    onClick = acoes.escolherApps
                )
            }

            TituloSecao("Filtros (opcional)")
            Text(
                "Sem filtros, qualquer notificação dos apps escolhidos toca. Com filtros, o app compara o nome da conversa e o texto da notificação só na memória do celular; nada disso é gravado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            Bloco {
                Column(Modifier.padding(16.dp)) {
                    CampoLista("Só destes contatos ou grupos", "Ex.: Plantão TI", r.contatos) { v -> acoes.mudar { it.copy(contatos = v) } }
                    Spacer(Modifier.height(14.dp))
                    CampoLista("Só se tiver estas palavras", "Ex.: urgente", r.palavras) { v -> acoes.mudar { it.copy(palavras = v) } }
                    Spacer(Modifier.height(14.dp))
                    CampoLista("Nunca tocar se tiver", "Ex.: bom dia", r.ignorar) { v -> acoes.mudar { it.copy(ignorar = v) } }
                }
            }

            TituloSecao("Quando vale")
            Column(Modifier.padding(horizontal = 20.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = r.quando == QuandoVale.NO_PLANTAO,
                        onClick = { acoes.mudar { it.copy(quando = QuandoVale.NO_PLANTAO) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("No plantão") }
                    SegmentedButton(
                        selected = r.quando == QuandoVale.SEMPRE,
                        onClick = { acoes.mudar { it.copy(quando = QuandoVale.SEMPRE) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Sempre") }
                }
                Text(
                    if (r.quando == QuandoVale.SEMPRE) "Toca a qualquer hora, mesmo fora do plantão. Use para algo realmente urgente."
                    else "Toca só durante os plantões com alertas ligados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (r.quando == QuandoVale.NO_PLANTAO && plantoes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    EscolhaPlantoes(plantoes, r.plantoes) { v -> acoes.mudar { it.copy(plantoes = v) } }
                }
            }

            TituloSecao("Como toca")
            Column(Modifier.padding(horizontal = 20.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !r.somProprio,
                        onClick = { acoes.usarSomProprio(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Som padrão") }
                    SegmentedButton(
                        selected = r.somProprio,
                        onClick = { acoes.usarSomProprio(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Som próprio") }
                }
                Text(
                    if (r.somProprio) "Esta regra toca do jeito abaixo, mesmo que o som padrão mude."
                    else "Esta regra toca o som padrão do app. Mudar o som padrão muda o de todas as regras que usam ele.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                )
            }
            Bloco {
                if (r.somProprio) {
                    OpcoesToque(r.toque, AcoesToque(acoes.mudarToque, acoes.escolherSom, acoes.previaVibracao))
                } else {
                    LinhaAjuste(
                        Icons.Rounded.MusicNote,
                        "Som padrão do app",
                        resumoToque(padrao),
                        corIcone = Extras.cores.sinal,
                        onClick = acoes.abrirSomPadrao
                    )
                }
            }

            TituloSecao("Mais opções")
            Bloco {
                LinhaInterruptor(
                    Icons.Rounded.Call, "Também em chamadas", "Chamadas de voz pelos apps escolhidos tocam até você atender.",
                    r.chamadas, { v -> acoes.mudar { it.copy(chamadas = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    if (r.critica) Icons.Rounded.PriorityHigh else Icons.Rounded.LowPriority,
                    "Prioridade máxima",
                    "É conferida antes das outras e toca mesmo se você estiver numa ligação.",
                    r.critica, { v -> acoes.mudar { it.copy(critica = v) } }
                )
                Divisoria()
                val silenciada = r.silenciada(agora)
                LinhaComConteudo(
                    Icons.Rounded.NotificationsPaused,
                    "Silenciar por um tempo",
                    if (silenciada) "Silenciada até ${Formato.quando(r.silenciadaAte, agora)}." else "A regra volta sozinha depois."
                ) {
                    if (silenciada) {
                        TextButton(onClick = { acoes.silenciar(0) }) { Text("Voltar a tocar agora") }
                    } else {
                        Fichas(
                            listOf(-1 to "Não", 60 to "1 hora", 240 to "4 horas", 480 to "8 horas"),
                            -1,
                            { v -> if (v > 0) acoes.silenciar(v) }
                        )
                    }
                }
            }
        }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir esta regra?") },
            text = { Text("\"${r.nome.ifBlank { "Regra" }}\" deixa de disparar alertas.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; acoes.excluir() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EscolhaPlantoes(plantoes: List<Pair<String, String>>, escolhidos: Set<String>, onMudar: (Set<String>) -> Unit) {
    Text("Em quais plantões", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = escolhidos.isEmpty(), onClick = { onMudar(emptySet()) }, label = { Text("Todos") })
        for ((id, nome) in plantoes) {
            val sel = id in escolhidos
            FilterChip(
                selected = sel,
                onClick = { onMudar(if (sel) escolhidos - id else escolhidos + id) },
                label = { Text(nome) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampoLista(rotulo: String, exemplo: String, itens: List<String>, onMudar: (List<String>) -> Unit) {
    var texto by remember { mutableStateOf("") }
    fun adicionar() {
        val v = texto.trim()
        if (v.isNotEmpty() && itens.none { it.equals(v, ignoreCase = true) }) onMudar(itens + v)
        texto = ""
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it.take(60) },
        label = { Text(rotulo) },
        placeholder = { Text(exemplo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { adicionar() }),
        trailingIcon = {
            IconButton(onClick = { adicionar() }, enabled = texto.isNotBlank()) {
                Icon(Icons.Rounded.Add, contentDescription = "Adicionar")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (itens.isNotEmpty()) {
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in itens) {
                InputChip(
                    selected = false,
                    onClick = { onMudar(itens - item) },
                    label = { Text(item) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Close, contentDescription = "Remover $item", modifier = Modifier.size(InputChipDefaults.IconSize))
                    }
                )
            }
        }
    }
}
