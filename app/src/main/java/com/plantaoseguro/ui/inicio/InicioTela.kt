package com.plantaoseguro.ui.inicio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.ui.comum.EscalaSemana
import com.plantaoseguro.ui.comum.EstadoGiroflex
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.Giroflex
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.EstiloHorario
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.ui.tema.Paleta
import com.plantaoseguro.util.Formato

/** Ações da tela inicial (agrupadas para não virar uma lista enorme de parâmetros). */
class AcoesInicio(
    val novo: () -> Unit = {},
    val editar: (String) -> Unit = {},
    val ligar: (String, Boolean) -> Unit = { _, _ -> },
    val folga: (String) -> Unit = {},
    val desfazerFolga: (String) -> Unit = {},
    val comecar: (Int) -> Unit = {},
    val encerrar: () -> Unit = {},
    val testar: () -> Unit = {},
    val parar: () -> Unit = {},
    val resolver: () -> Unit = {}
)

@Composable
fun InicioTela(e: InicioEstado, acoes: AcoesInicio, animar: Boolean = true) {
    var mostrarComecar by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "Plantão Seguro",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)
                )
            }
            item { Palco(e, animar, { mostrarComecar = true }, acoes) }
            if (e.alerta == null && e.pendente != null) {
                item { AvisoPendente(e, acoes) }
            }
            if (!e.escutaLigada) {
                item {
                    Aviso(
                        "A escuta das notificações está desligada",
                        "O Android desligou a parte do app que percebe as mensagens. Toque para resolver.",
                        acoes.resolver
                    )
                }
            } else if (e.pendencias.isNotEmpty()) {
                item {
                    Aviso(
                        if (e.pendencias.size == 1) "Falta liberar 1 permissão" else "Faltam liberar ${e.pendencias.size} permissões",
                        "Sem isso o alerta pode não tocar.",
                        acoes.resolver
                    )
                }
            }
            item { TituloSecao("Próximos 7 dias") }
            item { EscalaSemana(e.semana, Modifier.padding(start = 12.dp, end = 20.dp)) }
            item { TituloSecao("Seus plantões") }
            if (e.plantoes.isEmpty()) {
                item { SemPlantoes(acoes.novo) }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column {
                            e.plantoes.forEachIndexed { i, r ->
                                if (i > 0) {
                                    HorizontalDivider(
                                        Modifier.padding(start = 36.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                    )
                                }
                                LinhaPlantao(r, acoes)
                            }
                        }
                    }
                }
            }
        }

        if (e.plantoes.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = acoes.novo,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Novo plantão") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).semantics { contentDescription = "Novo plantão" }
            )
        }
    }

    if (mostrarComecar) {
        FolhaComecar(
            duracaoPadrao = e.duracaoRapidoMin,
            onComecar = { mostrarComecar = false; acoes.comecar(it) },
            onFechar = { mostrarComecar = false }
        )
    }
}

@Composable
private fun Palco(e: InicioEstado, animar: Boolean, onComecar: () -> Unit, acoes: AcoesInicio) {
    val extras = Extras.cores
    val escuroSistema = isSystemInDarkTheme()
    val alerta = e.alerta
    val tocando = alerta != null
    val emPlantao = e.emPlantao
    val fundo = when {
        tocando -> Color(0xFFB3161E)
        emPlantao -> extras.palco
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val destaque = tocando || emPlantao
    val texto = if (destaque) Color.White else MaterialTheme.colorScheme.onSurface
    val textoSuave = if (destaque) extras.noPalcoSuave else MaterialTheme.colorScheme.onSurfaceVariant
    val principal = e.ativas.firstOrNull()
    val apps = when (e.nomesApps.size) {
        0 -> "nenhum app"
        1 -> e.nomesApps[0]
        else -> e.nomesApps.dropLast(1).joinToString(", ") + " ou " + e.nomesApps.last()
    }
    val estadoLuz = when {
        tocando -> EstadoGiroflex.TOCANDO
        e.armado && emPlantao -> EstadoGiroflex.ARMADO
        else -> EstadoGiroflex.DESLIGADO
    }
    val titulo = when {
        alerta?.teste == true -> "Teste tocando"
        tocando -> "Alerta tocando"
        emPlantao -> "Em plantão"
        else -> "Fora do plantão"
    }
    val subtitulo = when {
        alerta?.teste == true -> "Se você está ouvindo, funciona."
        alerta != null -> alerta.appNome?.let { "Mensagem no $it" } ?: "Mensagem de trabalho"
        principal != null -> principal.plantao.nome
        else -> "Som normal no celular"
    }
    val descricao = when {
        tocando -> "Toque em Parar ou abra a mensagem para silenciar."
        principal != null && principal.plantao.tocarSirene && e.regrasLigadas > 0 ->
            "O alerta toca quando chegar mensagem no $apps, mesmo no silencioso."
        principal != null && principal.plantao.tocarSirene -> "Nenhuma regra de alerta está ligada."
        principal != null -> "Sem alertas neste plantão. O som do celular fica ligado."
        e.proxima != null -> "Próximo plantão: ${e.proxima.plantao.nome}, ${Formato.quando(e.proxima.inicio, e.agora)}."
        else -> "Nenhum plantão programado. Cadastre um ou comece agora."
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = fundo
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Giroflex(
                    estado = estadoLuz,
                    tamanho = 92.dp,
                    fundoEscuro = destaque || escuroSistema,
                    animar = animar,
                    modifier = Modifier.semantics { contentDescription = "Estado: $titulo" }
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titulo, style = MaterialTheme.typography.displaySmall, color = texto)
                    Text(subtitulo, style = MaterialTheme.typography.titleMedium, color = textoSuave, maxLines = 2)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(descricao, style = MaterialTheme.typography.bodyLarge, color = texto)
            if (principal != null && !tocando) {
                Text(
                    "Termina ${Formato.quando(principal.fim, e.agora)}. Faltam ${Formato.duracao(principal.fim - e.agora)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textoSuave,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (!emPlantao && !tocando && e.regrasSempre > 0) {
                Text(
                    if (e.regrasSempre == 1) "1 regra vale sempre, mesmo fora do plantão." else "${e.regrasSempre} regras valem sempre, mesmo fora do plantão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textoSuave,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            when {
                tocando -> Button(
                    onClick = acoes.parar,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3161E))
                ) { Text("Parar alerta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

                emPlantao -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = acoes.testar,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = extras.sinal, contentColor = Color.White)
                    ) { Text("Testar alerta") }
                    OutlinedButton(
                        onClick = acoes.encerrar,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                    ) { Text("Encerrar") }
                }

                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onComecar, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Começar agora")
                    }
                    OutlinedButton(onClick = acoes.testar, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Testar alerta")
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoPendente(e: InicioEstado, acoes: AcoesInicio) {
    val p = e.pendente ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationImportant, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text("Alerta sem confirmação", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "${p.appNome?.let { "Mensagem no $it" } ?: "Mensagem"} às ${Formato.hora(p.inicio)}. Tocou até o fim e ninguém parou.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(onClick = acoes.parar, modifier = Modifier.align(Alignment.End)) { Text("Já vi") }
        }
    }
}

@Composable
private fun Aviso(titulo: String, texto: String, onClick: () -> Unit) {
    val aviso = Extras.cores.aviso
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = aviso.copy(alpha = 0.14f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = aviso)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Resolver", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LinhaPlantao(r: ResumoPlantao, acoes: AcoesInicio) {
    val cor = Paleta.corEscala(r.plantao.cor)
    val ligado = r.plantao.ligado
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { acoes.editar(r.plantao.id) }
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(64.dp)
                .background(if (ligado) cor else cor.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.plantao.nome,
                style = MaterialTheme.typography.titleMedium,
                color = if (ligado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(r.quando, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                r.horario,
                style = EstiloHorario,
                color = if (ligado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (r.situacao != null) {
                Text(
                    r.situacao,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.valendo) Extras.cores.sinal else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = ligado,
            onCheckedChange = { acoes.ligar(r.plantao.id, it) },
            modifier = Modifier.semantics { contentDescription = "Ligar o plantão ${r.plantao.nome}" }
        )
        if (r.plantao.tipo == TipoAgenda.SEMANAL) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções de ${r.plantao.nome}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Folga na próxima vez") },
                        onClick = { menu = false; acoes.folga(r.plantao.id) }
                    )
                    if (r.temFolga) {
                        DropdownMenuItem(
                            text = { Text("Desfazer folgas") },
                            onClick = { menu = false; acoes.desfazerFolga(r.plantao.id) }
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun SemPlantoes(onNovo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Cadastre seu primeiro plantão", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "O alerta só toca nos horários que você cadastrar. Fora deles, o celular continua como sempre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onNovo) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Novo plantão")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaComecar(duracaoPadrao: Int, onComecar: (Int) -> Unit, onFechar: () -> Unit) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var minutos by remember { mutableIntStateOf(duracaoPadrao) }
    val opcoes = listOf(60, 120, 240, 480, 720, 1440).let { if (duracaoPadrao in it) it else (it + duracaoPadrao).sorted() }
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = estado) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Começar plantão agora", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Os alertas ficam ligados pelo tempo que você escolher. Dá para encerrar antes a qualquer momento.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Fichas(
                opcoes = opcoes.map { it to Formato.duracao(it * 60_000L) },
                selecionada = minutos,
                onEscolher = { minutos = it }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Termina ${Formato.quando(System.currentTimeMillis() + minutos * 60_000L)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onComecar(minutos) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Extras.cores.sinal, contentColor = Color.White)
            ) { Text("Começar plantão") }
        }
    }
}
