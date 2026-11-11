package com.plantaoseguro.ui.plantao

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plantaoseguro.agenda.Agenda
import com.plantaoseguro.modelo.AoTerminar
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.ui.comum.BarraTopo
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.LinhaComConteudo
import com.plantaoseguro.ui.comum.LinhaInterruptor
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.EstiloHorario
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.ui.tema.Paleta
import com.plantaoseguro.util.Formato
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Modelos prontos: preenchem nome, dias, horários e o que acontece. */
private class Modelo(val nome: String, val aplicar: (Plantao) -> Plantao)

private val MODELOS = listOf(
    Modelo("Plantão noturno") { it.copy(nome = "Plantão noturno", tipo = TipoAgenda.SEMANAL, dias = setOf(1, 2, 3, 4, 5), inicioMin = 19 * 60, fimMin = 7 * 60, tocarSirene = true) },
    Modelo("Sobreaviso") { it.copy(nome = "Sobreaviso", tipo = TipoAgenda.SEMANAL, dias = (1..7).toSet(), inicioMin = 18 * 60, fimMin = 8 * 60, tocarSirene = true) },
    Modelo("Fim de semana") { it.copy(nome = "Fim de semana", tipo = TipoAgenda.SEMANAL, dias = setOf(6, 7), inicioMin = 8 * 60, fimMin = 8 * 60, tocarSirene = true) },
    Modelo("Expediente") {
        it.copy(
            nome = "Expediente", tipo = TipoAgenda.SEMANAL, dias = setOf(1, 2, 3, 4, 5),
            inicioMin = 7 * 60 + 50, fimMin = 17 * 60, tocarSirene = false,
            tirarDoSilencioso = true, vigiarVolume = true, aoTerminar = AoTerminar.MANTER
        )
    }
)
private val NOMES_DIAS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTela(
    p: Plantao,
    novo: Boolean,
    erro: String?,
    onMudar: ((Plantao) -> Plantao) -> Unit,
    onSalvar: () -> Unit,
    onExcluir: () -> Unit,
    onVoltar: () -> Unit
) {
    var confirmarExclusao by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BarraTopo(if (novo) "Novo plantão" else "Editar plantão", onVoltar) {
                if (!novo) {
                    IconButton(onClick = { confirmarExclusao = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Excluir plantão")
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (erro != null) {
                        Text(
                            erro,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(onClick = onSalvar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (novo) "Salvar plantão" else "Salvar alterações")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = p.nome,
                    onValueChange = { v -> onMudar { it.copy(nome = v.take(40)) } },
                    label = { Text("Nome") },
                    placeholder = { Text("Ex.: Sobreaviso noturno") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                if (p.nome.isBlank()) {
                    Row(
                        Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MODELOS.forEach { m ->
                            AssistChip(onClick = { onMudar(m.aplicar) }, label = { Text(m.nome) })
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Cor na escala", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Paleta.escala.forEachIndexed { i, cor ->
                        val sel = p.cor == i
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(cor, CircleShape)
                                .then(
                                    if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                )
                                .clickable { onMudar { it.copy(cor = i) } }
                                .semantics { contentDescription = "Cor ${i + 1}" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = p.tipo == TipoAgenda.SEMANAL,
                        onClick = { onMudar { it.copy(tipo = TipoAgenda.SEMANAL) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Toda semana") }
                    SegmentedButton(
                        selected = p.tipo == TipoAgenda.UNICO,
                        onClick = { onMudar { it.copy(tipo = TipoAgenda.UNICO) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Uma vez") }
                }
                Spacer(Modifier.height(18.dp))
                if (p.tipo == TipoAgenda.SEMANAL) Semanal(p, onMudar) else Unico(p, onMudar)
            }

            TituloSecao("Durante o plantão")
            Bloco {
                LinhaInterruptor(
                    Icons.Rounded.NotificationsActive,
                    "Ligar os alertas",
                    "As regras de alerta \"no plantão\" valem neste horário.",
                    p.tocarSirene,
                    { v -> onMudar { it.copy(tocarSirene = v) } },
                    corIcone = Extras.cores.sinal
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    "Tirar do silencioso",
                    "O toque normal do celular também volta a soar.",
                    p.tirarDoSilencioso,
                    { v -> onMudar { it.copy(tirarDoSilencioso = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.Rounded.DoNotDisturbOn,
                    "Desligar o Não Perturbe",
                    "Para nada bloquear os avisos de trabalho.",
                    p.desligarNaoPerturbe,
                    { v -> onMudar { it.copy(desligarNaoPerturbe = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.Rounded.Hearing,
                    "Vigiar o volume",
                    "Se você baixar o volume do toque, o app pergunta por quanto tempo manter.",
                    p.vigiarVolume,
                    { v -> onMudar { it.copy(vigiarVolume = v) } }
                )
                if (p.vigiarVolume) {
                    LinhaComConteudo(Icons.AutoMirrored.Rounded.VolumeUp, "Volume mínimo", "Abaixo disso o app pergunta.") {
                        Fichas(
                            listOf(40 to "40%", 55 to "55%", 70 to "70%", 85 to "85%", 100 to "100%"),
                            p.volumeMinimo,
                            { v -> onMudar { it.copy(volumeMinimo = v) } }
                        )
                    }
                }
            }

            TituloSecao("Quando o plantão terminar")
            Bloco {
                OpcaoFim(
                    "Voltar o som como estava",
                    "Se o celular estava no silencioso antes, volta para o silencioso.",
                    p.aoTerminar == AoTerminar.RESTAURAR
                ) { onMudar { it.copy(aoTerminar = AoTerminar.RESTAURAR) } }
                Divisoria()
                OpcaoFim(
                    "Deixar o som ligado",
                    "Só desliga a sirene. O resto fica como estiver.",
                    p.aoTerminar == AoTerminar.MANTER
                ) { onMudar { it.copy(aoTerminar = AoTerminar.MANTER) } }
            }
        }
    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir este plantão?") },
            text = { Text("\"${p.nome.ifBlank { "Plantão" }}\" sai da escala e a sirene deixa de tocar nesses horários.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; onExcluir() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Semanal(p: Plantao, onMudar: ((Plantao) -> Plantao) -> Unit) {
    var editando by remember { mutableStateOf<String?>(null) }

    Text("Dias", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val sel = d in p.dias
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .selectable(selected = sel, role = Role.Checkbox) {
                        onMudar { it.copy(dias = if (sel) it.dias - d else it.dias + d) }
                    }
                    .semantics { contentDescription = NOMES_DIAS[d - 1] },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    Formato.DIAS_LETRA[d - 1],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoHorario("Começa", Formato.horaMin(p.inicioMin), Modifier.weight(1f)) { editando = "inicio" }
        CampoHorario("Termina", Formato.horaMin(p.fimMin), Modifier.weight(1f)) { editando = "fim" }
    }
    Spacer(Modifier.height(8.dp))
    val dur = Formato.duracao(Agenda.duracaoMin(p) * 60_000L)
    Text(
        if (Agenda.terminaNoDiaSeguinte(p)) "Termina no dia seguinte. Duração de $dur." else "Duração de $dur.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when (editando) {
        "inicio" -> DialogoHora("Começa às", p.inicioMin, { m -> onMudar { it.copy(inicioMin = m) } }) { editando = null }
        "fim" -> DialogoHora("Termina às", p.fimMin, { m -> onMudar { it.copy(fimMin = m) } }) { editando = null }
    }
}

@Composable
private fun Unico(p: Plantao, onMudar: ((Plantao) -> Plantao) -> Unit) {
    val zona = ZoneId.systemDefault()
    var editando by remember { mutableStateOf<String?>(null) }

    fun dataDe(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
    fun minDe(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zona).toLocalTime().let { it.hour * 60 + it.minute }
    fun juntar(d: LocalDate, min: Int): Long =
        d.atTime(LocalTime.of(min / 60, min % 60)).atZone(zona).toInstant().toEpochMilli()

    Text("Começa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoHorario("Dia", Formato.diaMes(p.inicioMs, zona), Modifier.weight(1f)) { editando = "dataInicio" }
        CampoHorario("Hora", Formato.hora(p.inicioMs, zona), Modifier.weight(1f)) { editando = "horaInicio" }
    }
    Spacer(Modifier.height(14.dp))
    Text("Termina", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoHorario("Dia", Formato.diaMes(p.fimMs, zona), Modifier.weight(1f)) { editando = "dataFim" }
        CampoHorario("Hora", Formato.hora(p.fimMs, zona), Modifier.weight(1f)) { editando = "horaFim" }
    }
    if (p.fimMs > p.inicioMs) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Duração de ${Formato.duracao(p.fimMs - p.inicioMs)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    when (editando) {
        "dataInicio" -> DialogoData(dataDe(p.inicioMs), { d ->
            onMudar {
                val novoInicio = juntar(d, minDe(it.inicioMs))
                val dur = (it.fimMs - it.inicioMs).coerceAtLeast(60 * 60_000L)
                it.copy(inicioMs = novoInicio, fimMs = novoInicio + dur)
            }
        }) { editando = null }
        "horaInicio" -> DialogoHora("Começa às", minDe(p.inicioMs), { m ->
            onMudar {
                val novoInicio = juntar(dataDe(it.inicioMs), m)
                val dur = (it.fimMs - it.inicioMs).coerceAtLeast(60 * 60_000L)
                it.copy(inicioMs = novoInicio, fimMs = novoInicio + dur)
            }
        }) { editando = null }
        "dataFim" -> DialogoData(dataDe(p.fimMs), { d -> onMudar { it.copy(fimMs = juntar(d, minDe(it.fimMs))) } }) { editando = null }
        "horaFim" -> DialogoHora("Termina às", minDe(p.fimMs), { m -> onMudar { it.copy(fimMs = juntar(dataDe(it.fimMs), m)) } }) { editando = null }
    }
}

@Composable
private fun CampoHorario(rotulo: String, valor: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = EstiloHorario.copy(fontSize = 30.sp, lineHeight = 34.sp))
        }
    }
}

@Composable
private fun OpcaoFim(titulo: String, explicacao: String, selecionada: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selecionada, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selecionada, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(explicacao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(titulo: String, inicial: Int, onEscolher: (Int) -> Unit, onFechar: () -> Unit) {
    val estado = rememberTimePickerState(initialHour = inicial / 60, initialMinute = inicial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(titulo) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = estado) }
        },
        confirmButton = {
            TextButton(onClick = { onEscolher(estado.hour * 60 + estado.minute); onFechar() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoData(inicial: LocalDate, onEscolher: (LocalDate) -> Unit, onFechar: () -> Unit) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let {
                    onEscolher(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onFechar()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    ) {
        DatePicker(state = estado, showModeToggle = false)
    }
}
