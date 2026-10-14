package com.plantaoseguro.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plantaoseguro.BuildConfig
import com.plantaoseguro.dados.Backup
import com.plantaoseguro.dados.Repositorio
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.sirene.Catalogo
import com.plantaoseguro.sistema.Apps
import com.plantaoseguro.sistema.Correcao
import com.plantaoseguro.sistema.Diagnostico
import com.plantaoseguro.sistema.Permissoes
import com.plantaoseguro.sistema.Requisito
import com.plantaoseguro.ui.ajuda.AjudaTela
import com.plantaoseguro.ui.ajuda.TipoAjuda
import com.plantaoseguro.ui.ajustes.AcoesAjustes
import com.plantaoseguro.ui.ajustes.AjustesTela
import com.plantaoseguro.ui.apps.AppsTela
import com.plantaoseguro.ui.apps.ItemApp
import com.plantaoseguro.ui.boasvindas.AcoesBoasVindas
import com.plantaoseguro.ui.boasvindas.BoasVindasTela
import com.plantaoseguro.ui.boasvindas.DepoisDasBoasVindas
import com.plantaoseguro.ui.boasvindas.PASSOS_BOAS_VINDAS
import com.plantaoseguro.ui.diagnostico.AcoesDiagnostico
import com.plantaoseguro.ui.diagnostico.DiagnosticoTela
import com.plantaoseguro.ui.diagnostico.TesteEscuta
import com.plantaoseguro.ui.historico.FormatoExportacao
import com.plantaoseguro.ui.historico.HistoricoTela
import com.plantaoseguro.ui.inicio.AcoesInicio
import com.plantaoseguro.ui.inicio.InicioTela
import com.plantaoseguro.ui.inicio.InicioViewModel
import com.plantaoseguro.ui.permissoes.PermissoesTela
import com.plantaoseguro.ui.plantao.EditorTela
import com.plantaoseguro.ui.plantao.EditorViewModel
import com.plantaoseguro.ui.regras.AcoesRegra
import com.plantaoseguro.ui.regras.EscolhaSom
import com.plantaoseguro.ui.regras.RegraEditorTela
import com.plantaoseguro.ui.regras.RegraViewModel
import com.plantaoseguro.ui.regras.RegrasTela
import com.plantaoseguro.ui.regras.resumir
import com.plantaoseguro.ui.sons.AcoesSons
import com.plantaoseguro.ui.sons.SonsTela
import com.plantaoseguro.util.Exportar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private enum class Aba(val rota: String, val rotulo: String, val icone: ImageVector) {
    INICIO("inicio", "Início", Icons.Rounded.Shield),
    ALERTAS("alertas", "Alertas", Icons.Rounded.NotificationsActive),
    HISTORICO("historico", "Histórico", Icons.Rounded.History),
    AJUSTES("ajustes", "Ajustes", Icons.Rounded.Tune)
}

private const val CHAVE_SOM = "som_escolhido"
private const val CHAVE_SOM_ATUAL = "som_atual"
private const val CHAVE_APPS = "apps_escolhidos"
private const val CHAVE_APPS_ATUAIS = "apps_atuais"

private val versao: String get() = BuildConfig.VERSION_NAME

/**
 * Raiz do app: primeiros passos na primeira vez; depois, as abas.
 * [destino] é uma tela pedida de fora (notificação, fim dos primeiros passos).
 */
@Composable
fun AppPlantao(destino: String?, onDestinoAberto: () -> Unit) {
    val grafo = LocalGrafo.current
    val dados by grafo.repo.dados.collectAsStateWithLifecycle()
    if (!dados.ajustes.boasVindasConcluida) {
        BoasVindasRota()
        return
    }
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rotaAtual = entrada?.destination?.route
    val naAba = Aba.entries.any { it.rota == rotaAtual }

    Scaffold(
        bottomBar = {
            if (naAba) {
                NavigationBar {
                    for (aba in Aba.entries) {
                        NavigationBarItem(
                            selected = rotaAtual == aba.rota,
                            onClick = {
                                nav.navigate(aba.rota) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(aba.icone, contentDescription = null) },
                            label = { Text(aba.rotulo) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = if (naAba) pad.calculateBottomPadding() else 0.dp)) {
            NavHost(nav, startDestination = Aba.INICIO.rota) {
                aba(Aba.INICIO) { InicioRota(nav) }
                aba(Aba.ALERTAS) { RegrasRota(nav) }
                aba(Aba.HISTORICO) { HistoricoRota() }
                aba(Aba.AJUSTES) { AjustesRota(nav) }
                composable("plantao/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    EditorPlantaoRota(nav, e.arguments?.getString("id")?.takeIf { it != "novo" })
                }
                composable("regra/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    RegraRota(nav, e.arguments?.getString("id")?.takeIf { it != "nova" }, e.savedStateHandle)
                }
                composable("escolher-apps") { AppsRota(nav) }
                composable("escolher-som") { SonsRota(nav) }
                composable(MainActivity.DESTINO_PERMISSOES) { PermissoesRota(nav) }
                composable(MainActivity.DESTINO_DIAGNOSTICO) { DiagnosticoRota(nav) }
                composable("ajuda") { AjudaTela(TipoAjuda.AJUDA, versao, { nav.popBackStack() }, { nav.navigate("privacidade") }) }
                composable("privacidade") { AjudaTela(TipoAjuda.PRIVACIDADE, versao, { nav.popBackStack() }, {}) }
            }
            // Depois do NavHost: o grafo de navegação já existe quando o efeito roda.
            LaunchedEffect(destino) {
                if (destino != null) {
                    onDestinoAberto()
                    nav.navigate(destino) { launchSingleTop = true }
                }
            }
        }
    }
}

/** As abas não têm barra de topo própria: só precisam desviar da barra de status. */
private fun NavGraphBuilder.aba(aba: Aba, conteudo: @Composable () -> Unit) {
    composable(aba.rota) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) { conteudo() }
    }
}

// ------------------------------------------------------------------ abas

@Composable
private fun InicioRota(nav: NavController) {
    val app = LocalContext.current.applicationContext as Application
    val vm = vm { g -> InicioViewModel(g, app) }
    val e by vm.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.atualizarPendencias()
        onPauseOrDispose { }
    }
    InicioTela(
        e,
        AcoesInicio(
            novo = { nav.navigate("plantao/novo") },
            editar = { nav.navigate("plantao/$it") },
            ligar = vm::ligarDesligar,
            folga = vm::folga,
            desfazerFolga = vm::desfazerFolga,
            comecar = vm::comecarAgora,
            encerrar = vm::encerrar,
            testar = vm::testar,
            parar = vm::confirmar,
            resolver = { nav.navigate(MainActivity.DESTINO_DIAGNOSTICO) }
        )
    )
}

@Composable
private fun RegrasRota(nav: NavController) {
    val grafo = LocalGrafo.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val dados by grafo.repo.dados.collectAsStateWithLifecycle()
    val nomes = remember { HashMap<String, String>() }
    val agora = System.currentTimeMillis()
    RegrasTela(
        regras = dados.regras.map { r -> resumir(r, { p -> nomes.getOrPut(p) { Apps.nome(contexto, p) } }, agora) },
        onNova = { nav.navigate("regra/nova") },
        onEditar = { nav.navigate("regra/$it") },
        onLigar = { id, v -> escopo.launch { grafo.coordenador.ligarDesligarRegra(id, v) } }
    )
}

@Composable
private fun HistoricoRota() {
    val grafo = LocalGrafo.current
    val contexto = LocalContext.current
    val eventos by grafo.historico.eventos.collectAsStateWithLifecycle()
    HistoricoTela(
        eventos = eventos,
        agora = System.currentTimeMillis(),
        onExportar = { formato ->
            when (formato) {
                FormatoExportacao.TEXTO -> Exportar.compartilharArquivo(contexto, "historico-plantao-seguro.txt", Exportar.historicoTexto(eventos), "text/plain", "Exportar histórico")
                FormatoExportacao.CSV -> Exportar.compartilharArquivo(contexto, "historico-plantao-seguro.csv", Exportar.historicoCsv(eventos), "text/csv", "Exportar histórico")
            }
        },
        onLimpar = { grafo.historico.limpar() }
    )
}

@Composable
private fun AjustesRota(nav: NavController) {
    val grafo = LocalGrafo.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val dados by grafo.repo.dados.collectAsStateWithLifecycle()
    var pendencias by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        pendencias = Permissoes.pendentes(contexto).size
        onPauseOrDispose { }
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            val ok = Exportar.escreverTexto(contexto, uri, Backup.exportar(grafo.repo.dados.value, versao))
            Toast.makeText(contexto, if (ok) "Cópia guardada." else "Não consegui gravar o arquivo.", Toast.LENGTH_LONG).show()
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val texto = Exportar.lerTexto(contexto, uri)
            if (texto == null) {
                Toast.makeText(contexto, "Não consegui ler o arquivo.", Toast.LENGTH_LONG).show()
            } else {
                val (novos, resultado) = Backup.importar(texto, grafo.repo.dados.value)
                when (resultado) {
                    is Backup.Resultado.Erro -> Toast.makeText(contexto, resultado.motivo, Toast.LENGTH_LONG).show()
                    is Backup.Resultado.Ok -> escopo.launch {
                        grafo.repo.atualizar { novos ?: it }
                        grafo.historico.registrar(TipoEvento.SISTEMA, Nivel.OK, "Cópia restaurada", "${resultado.regras} regra(s) e ${resultado.plantoes} plantão(ões).")
                        grafo.coordenador.sincronizar("cópia restaurada")
                        Toast.makeText(
                            contexto,
                            "Cópia restaurada: ${resultado.regras} regra(s) e ${resultado.plantoes} plantão(ões)." +
                                if (resultado.sonsTrocados > 0) " ${resultado.sonsTrocados} som(ns) de arquivo voltaram ao padrão." else "",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    AjustesTela(
        a = dados.ajustes,
        pendencias = pendencias,
        versao = versao,
        acoes = AcoesAjustes(
            mudar = { f -> escopo.launch { grafo.repo.atualizar { d -> d.copy(ajustes = f(d.ajustes)) } } },
            permissoes = { nav.navigate(MainActivity.DESTINO_PERMISSOES) },
            diagnostico = { nav.navigate(MainActivity.DESTINO_DIAGNOSTICO) },
            testar = { grafo.coordenador.testar(null) },
            exportar = { exportar.launch("plantao-seguro-configuracoes.json") },
            importar = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) },
            ajuda = { nav.navigate("ajuda") },
            privacidade = { nav.navigate("privacidade") },
            compartilhar = { Exportar.compartilharApp(contexto) }
        )
    )
}

// ------------------------------------------------------------------ editores

@Composable
private fun EditorPlantaoRota(nav: NavController, id: String?) {
    val vm = vm(chave = "plantao-$id") { g -> EditorViewModel(g, id) }
    val p by vm.plantao.collectAsStateWithLifecycle()
    val erro by vm.erro.collectAsStateWithLifecycle()
    EditorTela(
        p = p,
        novo = vm.novo,
        erro = erro,
        onMudar = vm::mudar,
        onSalvar = { vm.salvar { nav.popBackStack() } },
        onExcluir = { vm.excluir { nav.popBackStack() } },
        onVoltar = { nav.popBackStack() }
    )
}

@Composable
private fun RegraRota(nav: NavController, id: String?, estado: androidx.lifecycle.SavedStateHandle) {
    val grafo = LocalGrafo.current
    val contexto = LocalContext.current
    val vm = vm(chave = "regra-$id") { g -> RegraViewModel(g, id) }
    val r by vm.regra.collectAsStateWithLifecycle()
    val erro by vm.erro.collectAsStateWithLifecycle()

    // Resultados das telas de som e de apps.
    val somDevolvido by estado.getStateFlow<String?>(CHAVE_SOM, null).collectAsStateWithLifecycle()
    LaunchedEffect(somDevolvido) {
        somDevolvido?.let {
            runCatching { Repositorio.JSON.decodeFromString(EscolhaSom.serializer(), it) }.getOrNull()?.let(vm::aplicarSom)
            estado[CHAVE_SOM] = null
        }
    }
    val appsDevolvidos by estado.getStateFlow<String?>(CHAVE_APPS, null).collectAsStateWithLifecycle()
    LaunchedEffect(appsDevolvidos) {
        appsDevolvidos?.let {
            runCatching { Repositorio.JSON.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()?.let { l -> vm.aplicarApps(l.toSet()) }
            estado[CHAVE_APPS] = null
        }
    }

    RegraEditorTela(
        r = r,
        nova = vm.nova,
        erro = erro,
        nomesApps = r.apps.map { Apps.nome(contexto, it) }.sorted(),
        plantoes = vm.plantoes,
        agora = System.currentTimeMillis(),
        acoes = AcoesRegra(
            mudar = vm::mudar,
            mudarToque = vm::mudarToque,
            escolherApps = {
                estado[CHAVE_APPS_ATUAIS] = Repositorio.JSON.encodeToString(ListSerializer(String.serializer()), r.apps.toList())
                nav.navigate("escolher-apps")
            },
            escolherSom = {
                estado[CHAVE_SOM_ATUAL] = Repositorio.JSON.encodeToString(EscolhaSom.serializer(), EscolhaSom(r.toque.som, r.toque.volume))
                nav.navigate("escolher-som")
            },
            previaVibracao = vm::previaVibracao,
            testar = vm::testar,
            silenciar = vm::silenciar,
            salvar = { vm.salvar { nav.popBackStack() } },
            excluir = { vm.excluir { nav.popBackStack() } },
            voltar = {
                grafo.sirene.pararPrevia()
                nav.popBackStack()
            }
        )
    )
}

@Composable
private fun AppsRota(nav: NavController) {
    val contexto = LocalContext.current
    val anterior = nav.previousBackStackEntry?.savedStateHandle
    var escolhidos by rememberSaveable {
        mutableStateOf(
            anterior?.get<String>(CHAVE_APPS_ATUAIS)?.let {
                runCatching { Repositorio.JSON.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()?.toSet()
            } ?: emptySet()
        )
    }
    val sugeridos by produceState(initialValue = emptyList<ItemApp>()) {
        value = withContext(Dispatchers.IO) {
            Apps.sugeridos.filter { Apps.instalado(contexto, it.pacote) }
                .map { ItemApp(it.pacote, Apps.nome(contexto, it.pacote), Apps.icone(contexto, it.pacote)) }
        }
    }
    var outros by remember { mutableStateOf<List<ItemApp>?>(null) }
    val escopo = rememberCoroutineScope()
    fun devolver() {
        anterior?.set(CHAVE_APPS, Repositorio.JSON.encodeToString(ListSerializer(String.serializer()), escolhidos.toList()))
        nav.popBackStack()
    }
    BackHandler { devolver() }
    AppsTela(
        sugeridos = sugeridos,
        outros = outros,
        escolhidos = escolhidos,
        onAlternar = { pkg, v -> escolhidos = if (v) escolhidos + pkg else escolhidos - pkg },
        onMostrarOutros = {
            escopo.launch {
                outros = withContext(Dispatchers.IO) {
                    val sug = Apps.sugeridos.map { it.pacote }.toSet()
                    Apps.comIcone(contexto).filter { it.pacote !in sug }
                        .map { ItemApp(it.pacote, it.nome, Apps.icone(contexto, it.pacote)) }
                }
            }
        },
        onVoltar = { devolver() }
    )
}

@Composable
private fun SonsRota(nav: NavController) {
    val grafo = LocalGrafo.current
    val contexto = LocalContext.current
    val anterior = nav.previousBackStackEntry?.savedStateHandle
    val inicial = remember {
        anterior?.get<String>(CHAVE_SOM_ATUAL)?.let {
            runCatching { Repositorio.JSON.decodeFromString(EscolhaSom.serializer(), it) }.getOrNull()
        } ?: EscolhaSom(SomRef.Embutido(Catalogo.padrao.id), 100)
    }
    var som by remember { mutableStateOf(inicial.som) }
    var volume by remember { mutableIntStateOf(inicial.volume) }
    val previa by grafo.sirene.previa.collectAsStateWithLifecycle()
    val doCelular by produceState(initialValue = emptyList<SomRef.Sistema>()) {
        value = withContext(Dispatchers.IO) { Catalogo.alarmesDoCelular(contexto) }
    }
    val arquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contexto.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val escolhido = SomRef.Arquivo(uri.toString(), Exportar.nomeDoArquivo(contexto, uri))
            som = escolhido
            grafo.sirene.previa(escolhido, volume)
        }
    }
    DisposableEffect(Unit) { onDispose { grafo.sirene.pararPrevia() } }
    SonsTela(
        selecionado = som,
        previa = previa,
        volume = volume,
        doCelular = doCelular,
        acoes = AcoesSons(
            escolher = { som = it },
            previa = { grafo.sirene.previa(it, volume) },
            pararPrevia = { grafo.sirene.pararPrevia() },
            volume = { volume = it },
            arquivo = { arquivo.launch(arrayOf("audio/*")) },
            usar = {
                anterior?.set(CHAVE_SOM, Repositorio.JSON.encodeToString(EscolhaSom.serializer(), EscolhaSom(som, volume)))
                nav.popBackStack()
            },
            voltar = { nav.popBackStack() }
        )
    )
}

// ------------------------------------------------------------------ proteção

@Composable
private fun PedirNotificacoes(): () -> Unit {
    val contexto = LocalContext.current
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Permissoes.abrir(contexto, Requisito.NOTIFICACOES)
    }
    return {
        if (Build.VERSION.SDK_INT >= 33) pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
        else Permissoes.abrir(contexto, Requisito.NOTIFICACOES)
    }
}

@Composable
private fun PermissoesRota(nav: NavController) {
    val contexto = LocalContext.current
    val grafo = LocalGrafo.current
    var status by remember { mutableStateOf(Permissoes.aplicaveis().map { it to Permissoes.ok(contexto, it) }) }
    LifecycleResumeEffect(Unit) {
        status = Permissoes.aplicaveis().map { it to Permissoes.ok(contexto, it) }
        SentinelaService.pedirReconexao(contexto)
        onPauseOrDispose { }
    }
    val pedirNotificacoes = PedirNotificacoes()
    val fabricante = remember { Permissoes.fabricante() }
    PermissoesTela(
        status = status,
        fabricante = fabricante,
        dicas = remember { Permissoes.dicas(contexto, fabricante) },
        onPermitir = { r -> if (r == Requisito.NOTIFICACOES) pedirNotificacoes() else Permissoes.abrir(contexto, r) },
        onDica = { d -> Permissoes.abrirPrimeiro(contexto, d.atalhos) },
        onTestar = { grafo.coordenador.testar(null) },
        onTestarBloqueado = {
            grafo.coordenador.testarComTelaBloqueada(10)
            Toast.makeText(contexto, "Bloqueie o celular agora. O alerta toca em 10 segundos.", Toast.LENGTH_LONG).show()
        },
        onVoltar = { nav.popBackStack() }
    )
}

@Composable
private fun DiagnosticoRota(nav: NavController) {
    val contexto = LocalContext.current
    val grafo = LocalGrafo.current
    val escopo = rememberCoroutineScope()
    var itens by remember { mutableStateOf(Diagnostico.verificar(contexto, grafo)) }
    var teste by remember { mutableStateOf<TesteEscuta>(TesteEscuta.NaoFeito) }
    val conectada by SentinelaService.conectada.collectAsStateWithLifecycle()
    LifecycleResumeEffect(conectada) {
        itens = Diagnostico.verificar(contexto, grafo)
        onPauseOrDispose { }
    }
    DiagnosticoTela(
        itens = itens,
        teste = teste,
        acoes = AcoesDiagnostico(
            corrigir = { c ->
                when (c) {
                    Correcao.PERMISSOES -> nav.navigate(MainActivity.DESTINO_PERMISSOES)
                    Correcao.RELIGAR_ESCUTA -> {
                        SentinelaService.pedirReconexao(contexto)
                        escopo.launch {
                            kotlinx.coroutines.delay(1500)
                            itens = Diagnostico.verificar(contexto, grafo)
                        }
                    }
                    Correcao.REGRAS -> nav.navigate(Aba.ALERTAS.rota)
                    Correcao.NENHUMA -> Unit
                }
            },
            testarEscuta = {
                teste = TesteEscuta.Rodando
                escopo.launch {
                    val ms = Diagnostico.testarEscuta(grafo)
                    teste = if (ms != null) TesteEscuta.Ok(ms) else TesteEscuta.Falhou
                    grafo.historico.registrar(
                        TipoEvento.TESTE, if (ms != null) Nivel.OK else Nivel.ERRO,
                        if (ms != null) "Teste da escuta: funcionou" else "Teste da escuta: falhou",
                        if (ms != null) "A notificação de teste foi percebida em $ms ms." else "A escuta não percebeu a notificação de teste em 6 segundos."
                    )
                    itens = Diagnostico.verificar(contexto, grafo)
                }
            },
            testarAlerta = { grafo.coordenador.testar(null) },
            testarBloqueado = {
                grafo.coordenador.testarComTelaBloqueada(10)
                Toast.makeText(contexto, "Bloqueie o celular agora. O alerta toca em 10 segundos.", Toast.LENGTH_LONG).show()
            },
            voltar = { nav.popBackStack() }
        )
    )
}

// ------------------------------------------------------------------ primeiros passos

@Composable
private fun BoasVindasRota() {
    val contexto = LocalContext.current
    val grafo = LocalGrafo.current
    val escopo = rememberCoroutineScope()
    val dados by grafo.repo.dados.collectAsStateWithLifecycle()
    var passo by rememberSaveable { mutableIntStateOf(0) }
    val apps by produceState(initialValue = emptyList<ItemApp>()) {
        value = withContext(Dispatchers.IO) {
            Apps.sugeridos.filter { Apps.instalado(contexto, it.pacote) }
                .map { ItemApp(it.pacote, Apps.nome(contexto, it.pacote), Apps.icone(contexto, it.pacote)) }
        }
    }
    val regraPadrao = dados.regras.firstOrNull { it.id == Regra.ID_PADRAO } ?: dados.regras.firstOrNull() ?: Regra.padrao()
    var escolhidos by rememberSaveable { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(apps) {
        if (escolhidos == null && apps.isNotEmpty()) {
            val instalados = apps.map { it.pacote }.toSet()
            val atuais = regraPadrao.apps.filter { it in instalados }.toSet()
            escolhidos = atuais.ifEmpty { setOf(apps.first().pacote) }
        }
    }
    var status by remember { mutableStateOf(Permissoes.aplicaveis().map { it to Permissoes.ok(contexto, it) }) }
    LifecycleResumeEffect(passo) {
        status = Permissoes.aplicaveis().map { it to Permissoes.ok(contexto, it) }
        onPauseOrDispose { }
    }
    val pedirNotificacoes = PedirNotificacoes()
    val fabricante = remember { Permissoes.fabricante() }
    fun salvarRegra(f: (Regra) -> Regra) {
        escopo.launch {
            grafo.repo.atualizar { d ->
                val atual = d.regras.firstOrNull { it.id == regraPadrao.id } ?: regraPadrao
                d.copy(regras = d.regras.filterNot { it.id == atual.id }.let { listOf(f(atual)) + it })
            }
        }
    }
    BackHandler(enabled = passo > 0) { passo-- }
    BoasVindasTela(
        passo = passo,
        apps = apps,
        appsEscolhidos = escolhidos ?: regraPadrao.apps,
        permissoes = status,
        fabricante = fabricante,
        dicas = remember { Permissoes.dicas(contexto, fabricante) },
        som = regraPadrao.toque.som,
        acoes = AcoesBoasVindas(
            alternarApp = { pkg, v -> escolhidos = (escolhidos ?: emptySet()).let { if (v) it + pkg else it - pkg } },
            permitir = { r -> if (r == Requisito.NOTIFICACOES) pedirNotificacoes() else Permissoes.abrir(contexto, r) },
            dica = { d -> Permissoes.abrirPrimeiro(contexto, d.atalhos) },
            escolherSom = { s ->
                salvarRegra { it.copy(toque = it.toque.copy(som = s, tocarSom = true)) }
                grafo.sirene.previa(s, regraPadrao.toque.volume)
            },
            testar = { grafo.coordenador.testar(regraPadrao.id) },
            testarBloqueado = {
                grafo.coordenador.testarComTelaBloqueada(10)
                Toast.makeText(contexto, "Bloqueie o celular agora. O alerta toca em 10 segundos.", Toast.LENGTH_LONG).show()
            },
            avancar = {
                if (passo == 1) escolhidos?.let { e -> if (e.isNotEmpty()) salvarRegra { it.copy(apps = e) } }
                if (passo < PASSOS_BOAS_VINDAS - 1) passo++
            },
            voltar = { if (passo > 0) passo-- },
            concluir = { depois ->
                grafo.sirene.pararPrevia()
                escopo.launch {
                    grafo.repo.atualizar { d -> d.copy(ajustes = d.ajustes.copy(boasVindasConcluida = true)) }
                    when (depois) {
                        DepoisDasBoasVindas.COMECAR_AGORA -> grafo.coordenador.comecarAgora(grafo.repo.dados.value.ajustes.duracaoRapidoMin)
                        DepoisDasBoasVindas.NOVO_PLANTAO -> MainActivity.destinoPendente = "plantao/novo"
                        DepoisDasBoasVindas.INICIO -> Unit
                    }
                    grafo.historico.registrar(TipoEvento.SISTEMA, Nivel.OK, "Primeiros passos concluídos", null)
                }
            }
        )
    )
}
