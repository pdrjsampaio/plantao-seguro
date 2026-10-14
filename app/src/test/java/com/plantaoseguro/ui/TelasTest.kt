package com.plantaoseguro.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.Dados
import com.plantaoseguro.modelo.Evento
import com.plantaoseguro.modelo.ModoDuracao
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.PKG_WHATSAPP_BUSINESS
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Pulo
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.sirene.AlarmeTela
import com.plantaoseguro.sistema.Correcao
import com.plantaoseguro.sistema.Fabricante
import com.plantaoseguro.sistema.Gravidade
import com.plantaoseguro.sistema.Permissoes
import com.plantaoseguro.sistema.Requisito
import com.plantaoseguro.sistema.Verificacao
import com.plantaoseguro.ui.ajuda.AjudaTela
import com.plantaoseguro.ui.ajuda.TipoAjuda
import com.plantaoseguro.ui.ajustes.AcoesAjustes
import com.plantaoseguro.ui.ajustes.AjustesTela
import com.plantaoseguro.ui.apps.AppsTela
import com.plantaoseguro.ui.apps.ItemApp
import com.plantaoseguro.ui.boasvindas.AcoesBoasVindas
import com.plantaoseguro.ui.boasvindas.BoasVindasTela
import com.plantaoseguro.ui.diagnostico.AcoesDiagnostico
import com.plantaoseguro.ui.diagnostico.DiagnosticoTela
import com.plantaoseguro.ui.diagnostico.TesteEscuta
import com.plantaoseguro.ui.historico.HistoricoTela
import com.plantaoseguro.ui.inicio.AcoesInicio
import com.plantaoseguro.ui.inicio.InicioTela
import com.plantaoseguro.ui.inicio.montarInicio
import com.plantaoseguro.ui.permissoes.PermissoesTela
import com.plantaoseguro.ui.plantao.EditorTela
import com.plantaoseguro.ui.regras.AcoesRegra
import com.plantaoseguro.ui.regras.RegraEditorTela
import com.plantaoseguro.ui.regras.RegrasTela
import com.plantaoseguro.ui.regras.resumir
import com.plantaoseguro.ui.sons.AcoesSons
import com.plantaoseguro.ui.sons.SonsTela
import com.plantaoseguro.ui.tema.PlantaoTema
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Fotografa cada tela com dados realistas (Roborazzi). As imagens servem para
 * revisar o desenho e para pegar regressões visuais; não substituem o teste
 * num celular de verdade.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TelasTest {

    @get:Rule
    val compose = createComposeRule()

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")
    private fun ms(dia: Int, hora: Int, min: Int = 0) =
        LocalDateTime.of(2026, 10, dia, hora, min).atZone(sp).toInstant().toEpochMilli()

    /** Terça, 13/10/2026, 23:40: no meio do plantão noturno. */
    private val agora get() = ms(13, 23, 40)

    private val noturno = Plantao(
        id = "noite", nome = "Noturno UTI", cor = 0, dias = setOf(1, 2, 3, 4, 5),
        inicioMin = 19 * 60, fimMin = 7 * 60
    )
    private val expediente = Plantao(
        id = "exp", nome = "Expediente", cor = 1, dias = setOf(1, 2, 3, 4, 5),
        inicioMin = 7 * 60 + 50, fimMin = 17 * 60, tocarSirene = false, vigiarVolume = true
    )
    private val cobertura by lazy {
        Plantao(
            id = "sab", nome = "Cobertura de sábado", cor = 2, tipo = TipoAgenda.UNICO,
            inicioMs = ms(17, 8), fimMs = ms(17, 20)
        )
    }
    private val regraChefia = Regra(
        id = "chefia", nome = "Chefia e coordenação", contatos = listOf("Coordenação", "Dr. Silva"), critica = true,
        toque = Toque(som = SomRef.Embutido("alarme_incendio"), modoDuracao = ModoDuracao.ATE_PARAR, vibracao = Vibracao.SOS)
    )
    private val regraUrgente by lazy {
        Regra(
            id = "urgente", nome = "Urgências a qualquer hora", palavras = listOf("urgente", "código azul"),
            quando = QuandoVale.SEMPRE, silenciadaAte = ms(14, 6),
            toque = Toque(som = SomRef.Embutido("chamada_enfermagem"), crescente = true, insistirMin = 5)
        )
    }

    private val dados by lazy {
        Dados(
            regras = listOf(Regra.padrao(), regraChefia, regraUrgente),
            plantoes = listOf(noturno, expediente, cobertura),
            ajustes = Ajustes(boasVindasConcluida = true),
        )
    }

    private val nomeDoApp: (String) -> String = {
        when (it) {
            PKG_WHATSAPP_BUSINESS -> "WhatsApp Business"
            "com.whatsapp" -> "WhatsApp"
            else -> it
        }
    }

    private val alerta by lazy {
        AlertaAtivo(
            id = 1, regraId = "chefia", regraNome = "Chefia e coordenação", toque = regraChefia.toque,
            pacote = PKG_WHATSAPP_BUSINESS, appNome = "WhatsApp Business",
            chaves = mapOf("a" to agora, "b" to agora), quantidade = 2, inicio = agora - 42_000,
            conversa = "Coordenação UTI", plantaoNome = "Noturno UTI"
        )
    }

    @Before
    fun fixarRelogioERegiao() {
        TimeZone.setDefault(TimeZone.getTimeZone(sp))
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    }

    private fun foto(nome: String, escuro: Boolean = false, conteudo: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlantaoTema(escuro = escuro) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { conteudo() }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png")
    }

    // ---------------------------------------------------------------- início

    @Test
    fun inicio_em_plantao() = foto("01_inicio_em_plantao") {
        InicioTela(montarInicio(dados, null, null, agora, emptyList(), nomeDoApp, true, sp), AcoesInicio(), animar = false)
    }

    @Test
    fun inicio_em_plantao_escuro() = foto("02_inicio_em_plantao_escuro", escuro = true) {
        InicioTela(montarInicio(dados, null, null, agora, emptyList(), nomeDoApp, true, sp), AcoesInicio(), animar = false)
    }

    @Test
    fun inicio_tocando() = foto("03_inicio_tocando") {
        InicioTela(montarInicio(dados, alerta, null, agora, emptyList(), nomeDoApp, true, sp), AcoesInicio(), animar = false)
    }

    @Test
    fun inicio_com_pendencias() = foto("04_inicio_com_pendencias") {
        val semPlantaoAgora = dados.copy(execucao = dados.execucao.copy(pulos = listOf(Pulo("noite", ms(13, 19)))))
        val pendente = alerta.copy(quantidade = 1, chaves = mapOf("a" to agora), conversa = null)
        InicioTela(
            montarInicio(semPlantaoAgora, null, pendente, agora, listOf(Requisito.BATERIA, Requisito.TELA_CHEIA), nomeDoApp, true, sp),
            AcoesInicio(), animar = false
        )
    }

    @Test
    fun inicio_primeira_vez() = foto("05_inicio_primeira_vez") {
        InicioTela(montarInicio(Dados(), null, null, agora, emptyList(), nomeDoApp, true, sp), AcoesInicio(), animar = false)
    }

    @Test
    fun inicio_escuta_desligada() = foto("06_inicio_escuta_desligada") {
        InicioTela(montarInicio(dados, null, null, agora, emptyList(), nomeDoApp, false, sp), AcoesInicio(), animar = false)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f)
    fun inicio_letra_grande() = foto("06b_inicio_letra_grande") {
        InicioTela(montarInicio(dados, null, null, agora, emptyList(), nomeDoApp, true, sp), AcoesInicio(), animar = false)
    }

    // ---------------------------------------------------------------- alerta

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, fontScale = 1.3f)
    fun alarme_letra_grande() = foto("08b_alarme_letra_grande", escuro = true) {
        AlarmeTela(alerta, mostrarConversa = true, volumePara = true, onParar = {}, onAbrir = {}, onSilenciar = {}, animar = false)
    }

    @Test
    fun alarme_mensagens() = foto("07_alarme_mensagens", escuro = true) {
        AlarmeTela(alerta, mostrarConversa = true, volumePara = true, onParar = {}, onAbrir = {}, onSilenciar = {}, animar = false)
    }

    @Test
    fun alarme_bloqueado_chamada() = foto("08_alarme_chamada_bloqueado", escuro = true) {
        AlarmeTela(
            alerta.copy(chamada = true, quantidade = 1), mostrarConversa = false, volumePara = false,
            onParar = {}, onAbrir = {}, onSilenciar = {}, animar = false
        )
    }

    // ---------------------------------------------------------------- regras

    @Test
    fun alertas() = foto("09_alertas") {
        RegrasTela(dados.regras.map { resumir(it, nomeDoApp, agora) }, {}, {}, { _, _ -> })
    }

    @Test
    @Config(qualifiers = "w412dp-h2000dp-xxhdpi")
    fun regra_editor() = foto("10_regra_editor") {
        RegraEditorTela(
            r = regraChefia.copy(apps = setOf(PKG_WHATSAPP_BUSINESS, "com.whatsapp")),
            nova = false, erro = null,
            nomesApps = listOf("WhatsApp", "WhatsApp Business"),
            plantoes = listOf("noite" to "Noturno UTI", "exp" to "Expediente", "sab" to "Cobertura de sábado"),
            agora = agora, acoes = AcoesRegra()
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h2400dp-xxhdpi")
    fun sons() = foto("11_sons") {
        SonsTela(
            selecionado = SomRef.Embutido("sirene_europeia"),
            previa = SomRef.Embutido("sirene_europeia"),
            volume = 80,
            doCelular = listOf(SomRef.Sistema("content://media/alarme/1", "Argon")),
            acoes = AcoesSons()
        )
    }

    // ---------------------------------------------------------------- plantões

    @Test
    @Config(qualifiers = "w412dp-h1600dp-xxhdpi")
    fun plantao_editor() = foto("12_plantao_editor") {
        EditorTela(noturno, novo = false, erro = null, onMudar = {}, onSalvar = {}, onExcluir = {}, onVoltar = {})
    }

    @Test
    @Config(qualifiers = "w412dp-h1600dp-xxhdpi")
    fun plantao_novo_unico() = foto("13_plantao_novo_unico") {
        EditorTela(cobertura, novo = true, erro = "O fim precisa ser depois do início.", onMudar = {}, onSalvar = {}, onExcluir = {}, onVoltar = {})
    }

    // ---------------------------------------------------------------- histórico, ajustes, diagnóstico

    @Test
    fun historico() = foto("14_historico") {
        val eventos = listOf(
            Evento(ms(13, 23, 31), TipoEvento.ALERTA_CONFIRMADO, Nivel.INFO, "Alerta confirmado", "Você tocou na notificação da mensagem, depois de 12 s tocando."),
            Evento(ms(13, 23, 30), TipoEvento.ALERTA, Nivel.OK, "Alerta: Chefia e coordenação", "WhatsApp Business, durante Noturno UTI."),
            Evento(ms(13, 19, 0), TipoEvento.PLANTAO_INICIO, Nivel.OK, "Plantão começou: Noturno UTI", "Vai até amanhã às 07:00. Alertas ligados."),
            Evento(ms(13, 17, 0), TipoEvento.PLANTAO_FIM, Nivel.INFO, "Plantão encerrado", "Expediente. Motivo: Horário."),
            Evento(ms(12, 3, 12), TipoEvento.ALERTA_NAO_CONFIRMADO, Nivel.AVISO, "Alerta não confirmado", "WhatsApp Business: tocou até o fim e ninguém parou."),
            Evento(ms(12, 2, 0), TipoEvento.PROBLEMA, Nivel.ERRO, "Escuta desligada pelo sistema", "Pedi para religar duas vezes seguidas sem sucesso.")
        ).sortedByDescending { it.t }
        HistoricoTela(eventos, agora, {}, {})
    }

    @Test
    @Config(qualifiers = "w412dp-h2000dp-xxhdpi")
    fun ajustes() = foto("15_ajustes") {
        AjustesTela(Ajustes(boasVindasConcluida = true), pendencias = 1, versao = "3.0.0", acoes = AcoesAjustes())
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun diagnostico() = foto("16_diagnostico") {
        DiagnosticoTela(
            itens = listOf(
                Verificacao("Acesso às notificações", Gravidade.OK, "Ligado e conectado agora."),
                Verificacao("Economia de bateria", Gravidade.AVISO, "O Android pode fechar o app durante a noite.", Correcao.PERMISSOES),
                Verificacao("Regras de alerta", Gravidade.OK, "3 regras ligadas, vigiando 2 apps."),
                Verificacao("Notificação em tela cheia", Gravidade.ERRO, "Sem ela o alerta não aparece com a tela bloqueada.", Correcao.PERMISSOES),
                Verificacao("Última mensagem percebida", Gravidade.INFO, "hoje às 23:30, do WhatsApp Business.")
            ),
            teste = TesteEscuta.Ok(184),
            acoes = AcoesDiagnostico()
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h1800dp-xxhdpi")
    fun permissoes() = foto("17_permissoes") {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        PermissoesTela(
            status = Requisito.entries.map { it to (it == Requisito.NOTIFICACOES || it == Requisito.ESCUTA) },
            fabricante = Fabricante.XIAOMI,
            dicas = Permissoes.dicas(contexto, Fabricante.XIAOMI),
            onPermitir = {}, onDica = {}, onTestar = {}, onTestarBloqueado = {}, onVoltar = {}
        )
    }

    @Test
    fun apps() = foto("18_apps") {
        AppsTela(
            sugeridos = listOf(
                ItemApp(PKG_WHATSAPP_BUSINESS, "WhatsApp Business", null),
                ItemApp("com.whatsapp", "WhatsApp", null),
                ItemApp("org.telegram.messenger", "Telegram", null)
            ),
            outros = null,
            escolhidos = setOf(PKG_WHATSAPP_BUSINESS),
            onAlternar = { _, _ -> }, onMostrarOutros = {}, onVoltar = {}
        )
    }

    @Test
    fun ajuda() = foto("19_ajuda") {
        AjudaTela(TipoAjuda.AJUDA, "3.0.0", {}, {})
    }

    // ---------------------------------------------------------------- primeiros passos

    private fun boasVindas(passo: Int) = foto("2${passo}_boas_vindas_passo_${passo + 1}") {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        BoasVindasTela(
            passo = passo,
            apps = listOf(
                ItemApp(PKG_WHATSAPP_BUSINESS, "WhatsApp Business", null),
                ItemApp("com.whatsapp", "WhatsApp", null)
            ),
            appsEscolhidos = setOf(PKG_WHATSAPP_BUSINESS),
            permissoes = Requisito.entries.map { it to (it == Requisito.NOTIFICACOES) },
            fabricante = Fabricante.SAMSUNG,
            dicas = Permissoes.dicas(contexto, Fabricante.SAMSUNG),
            som = SomRef.Embutido("sirene_classica"),
            acoes = AcoesBoasVindas(),
            animar = false
        )
    }

    @Test fun boas_vindas_1() = boasVindas(0)
    @Test fun boas_vindas_2() = boasVindas(1)
    @Test fun boas_vindas_3() = boasVindas(2)
    @Test fun boas_vindas_4() = boasVindas(3)
    @Test fun boas_vindas_5() = boasVindas(4)
}
