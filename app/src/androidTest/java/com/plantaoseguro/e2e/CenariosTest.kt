package com.plantaoseguro.e2e

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.R
import com.plantaoseguro.agenda.Agenda
import com.plantaoseguro.agenda.SistemaReceiver
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.servico.PlantaoService
import com.plantaoseguro.sistema.Diagnostico
import com.plantaoseguro.sistema.Gravidade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.regex.Pattern

/**
 * Cenários de ponta a ponta num emulador, com notificações de verdade.
 *
 * O app auxiliar `:simulador` faz o papel do WhatsApp: publica e fecha
 * notificações de mensagem quando o teste manda. A escuta de notificações,
 * o motor, a sirene, a tela cheia e o AlarmManager são os de verdade.
 *
 * O que um emulador NÃO prova (e o relatório diz): o comportamento do
 * WhatsApp real, a economia de bateria agressiva de cada fabricante, o som
 * saindo de fato pelo alto-falante e um reinício físico do aparelho.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CenariosTest {

    companion object {
        const val SIM = "com.plantaoseguro.simulador"
        private const val MIN = 60_000L
        private const val HORA = 60 * MIN

        private val inst get() = InstrumentationRegistry.getInstrumentation()
        private val device: UiDevice get() = UiDevice.getInstance(inst)
        private val app get() = inst.targetContext.applicationContext as PlantaoApp
        private val pkg get() = app.packageName
        private val servico get() = "$pkg/com.plantaoseguro.sentinela.SentinelaService"

        private val pasta: File by lazy { File(app.getExternalFilesDir(null), "e2e").apply { mkdirs() } }
        private val relatorio: File by lazy { File(pasta, "relatorio.md") }

        fun sh(cmd: String): String = device.executeShellCommand(cmd)

        fun anotar(linha: String) {
            relatorio.appendText(linha + "\n")
            Log.i("E2E", linha)
        }

        @BeforeClass
        @JvmStatic
        fun prepararAparelho() {
            relatorio.writeText("")
            anotar("# Testes no emulador")
            anotar("")
            anotar("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}).")
            val nm = app.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 34) {
                anotar(
                    "Tela cheia liberada por padrão para um app instalado fora da loja: " +
                        if (nm.canUseFullScreenIntent()) "sim." else "não (a pessoa precisa liberar; o app pede nos primeiros passos)."
                )
            }
            sh("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            sh("pm grant $SIM android.permission.POST_NOTIFICATIONS")
            sh("appops set $pkg USE_FULL_SCREEN_INTENT allow")
            sh("appops set $SIM USE_FULL_SCREEN_INTENT allow")
            sh("cmd notification allow_dnd $pkg")
            sh("cmd notification allow_listener $servico")
            sh("settings put system screen_off_timeout 1800000")
            anotar("")
            anotar("| Teste | Resultado | Observado |")
            anotar("|---|---|---|")
        }
    }

    private val g get() = app.grafo
    private var nota: String = ""
    private var inicioDoTeste = 0L

    @get:Rule
    val vigia = object : TestWatcher() {
        override fun succeeded(d: Description) {
            anotar("| ${d.methodName} | passou | $nota |")
        }

        override fun failed(e: Throwable, d: Description) {
            anotar("| ${d.methodName} | **falhou** | ${e.message?.lineSequence()?.firstOrNull()?.replace("|", "/")} |")
            runCatching { foto("falha_${d.methodName}") }
        }
    }

    // ---------------------------------------------------------------- utilidades

    private fun <T> naPrincipal(bloco: suspend () -> T): T = runBlocking(Dispatchers.Main) { bloco() }

    private fun esperar(ms: Long = 10_000, cond: () -> Boolean): Boolean {
        val fim = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < fim) {
            if (cond()) return true
            Thread.sleep(100)
        }
        return cond()
    }

    private fun foto(nome: String) {
        device.takeScreenshot(File(pasta, "$nome.png"))
    }

    /** Textos com "_" no lugar de espaço: o shell do teste não aceita aspas. */
    private fun postar(
        id: Int,
        texto: String,
        quando: Long = System.currentTimeMillis(),
        conversa: String = "Equipe_UTI",
        chamada: Boolean = false
    ) {
        sh(
            "am broadcast -n $SIM/.Comando -f 32 --es acao postar --ei id $id --es texto $texto " +
                "--el quando $quando --es conversa $conversa --ez chamada $chamada"
        )
    }

    private fun cancelar(id: Int) {
        sh("am broadcast -n $SIM/.Comando -f 32 --es acao cancelar --ei id $id")
    }

    private val tocando get() = g.central.tocando.value

    private val regraBase = Regra(
        id = "e2e", nome = "Teste E2E", apps = setOf(SIM),
        toque = Toque(som = SomRef.Embutido("curto_ping"), volume = 10, duracaoSeg = 60, vibracao = Vibracao.NENHUMA)
    )

    private fun configurar(
        regra: Regra = regraBase,
        ajustes: Ajustes = Ajustes(boasVindasConcluida = true),
        plantaoAgora: Boolean = true
    ) = naPrincipal {
        g.central.confirmar("preparação do teste")
        g.repo.atualizar { d ->
            d.copy(regras = listOf(regra), plantoes = emptyList(), ajustes = ajustes, execucao = d.execucao.copy(pulos = emptyList()))
        }
        g.coordenador.sincronizar("preparação do teste")
        if (plantaoAgora) g.coordenador.comecarAgora(120)
    }

    @Before
    fun antes() {
        sh("am broadcast -n $SIM/.Comando -f 32 --es acao cancelar_tudo")
        device.wakeUp()
        sh("wm dismiss-keyguard")
        device.pressHome()
        nota = ""
        inicioDoTeste = System.currentTimeMillis()
        assertTrue("a escuta de notificações está conectada", esperar(20_000) { SentinelaService.conectada.value })
    }

    @After
    fun depois() {
        naPrincipal { g.central.confirmar("fim do teste") }
        device.pressBack()
    }

    // ---------------------------------------------------------------- cenários pedidos

    @Test
    fun c01_mensagem_no_plantao_dispara() {
        configurar()
        postar(101, "Plantao_msg101")
        assertTrue("o alerta tocou", esperar { tocando != null })
        assertEquals(SIM, tocando!!.pacote)
        assertTrue("a sirene está tocando", naPrincipal { g.sirene.tocando() })
        assertTrue("o serviço do plantão está rodando", esperar { PlantaoService.rodando })
        val nm = app.getSystemService(NotificationManager::class.java)
        assertTrue("a notificação do alerta está na tela", nm.activeNotifications.any { it.id == Notificador.ID_ALERTA })
        foto("c01_alerta_com_tela_ligada")
        nota = "App em segundo plano, tela ligada: alerta tocou e a notificação do alerta apareceu."
    }

    @Test
    fun c02_irrelevantes_nao_disparam() {
        configurar()
        sh("cmd notification post -t Outro_app outro Texto_qualquer")
        postar(201, "Antiga_msg201", quando = System.currentTimeMillis() - 13 * HORA)
        postar(202, "Anterior_msg202", quando = System.currentTimeMillis() - 10 * MIN)
        Thread.sleep(3_000)
        assertNull("app não vigiado, mensagem antiga e mensagem de antes do plantão não tocam", tocando)
        naPrincipal { g.coordenador.encerrarAgora() }
        postar(203, "Fora_msg203")
        Thread.sleep(3_000)
        assertNull("fora do plantão não toca", tocando)
        nota = "App não vigiado, mensagem de 13 h atrás, mensagem de antes do plantão e mensagem fora do plantão: nenhuma tocou."
    }

    @Test
    fun c03_duplicadas_nao_duplicam() {
        configurar()
        val t = System.currentTimeMillis()
        postar(301, "Dup_msg301", quando = t)
        assertTrue(esperar { tocando != null })
        postar(301, "Dup_msg301", quando = t)
        Thread.sleep(2_000)
        assertEquals("a mesma notificação republicada não soma", 1, tocando!!.quantidade)
        naPrincipal { g.central.confirmar("teste") }
        postar(301, "Dup_msg301", quando = t)
        Thread.sleep(2_500)
        assertNull("mensagem já confirmada não toca de novo", tocando)
        postar(301, "Nova_msg301", quando = t + 5_000)
        assertTrue("mensagem nova na mesma conversa toca", esperar { tocando != null })
        nota = "Republicação igual não somou nem voltou a tocar depois de confirmada; mensagem nova na mesma conversa tocou."
    }

    @Test
    fun c04_tocar_na_notificacao_original_para() {
        configurar()
        postar(401, "Toque_msg401")
        assertTrue(esperar { tocando != null })
        device.openNotification()
        val alvo = device.wait(Until.findObject(By.textContains("msg401")), 10_000)
        assertNotNull("a notificação do app de mensagens aparece na aba", alvo)
        foto("c04_aba_de_notificacoes")
        alvo.click()
        assertTrue("o alerta parou", esperar { tocando == null })
        assertTrue("a conversa abriu", device.wait(Until.hasObject(By.textStartsWith("Conversa aberta")), 10_000))
        val ev = g.historico.eventos.value.first { it.tipo == TipoEvento.ALERTA_CONFIRMADO }
        assertTrue(ev.detalhe.orEmpty(), ev.detalhe.orEmpty().contains("tocou na notificação da mensagem"))
        nota = "Tocar na notificação do app de mensagens (motivo REASON_CLICK) parou o alerta e abriu a conversa."
    }

    @Test
    fun c05_parar_pela_notificacao_do_alerta() {
        configurar()
        postar(501, "Parar_msg501")
        assertTrue(esperar { tocando != null })
        device.openNotification()
        val botao: UiObject2? = device.wait(Until.findObject(By.text(Pattern.compile("Parar", Pattern.CASE_INSENSITIVE))), 8_000)
        val como = if (botao != null) {
            foto("c05_botao_parar_na_notificacao")
            botao.click()
            "tocando no botão Parar da notificação"
        } else {
            val nm = app.getSystemService(NotificationManager::class.java)
            val n = nm.activeNotifications.first { it.id == Notificador.ID_ALERTA }.notification
            n.actions.first { it.title.toString() == "Parar" }.actionIntent.send()
            "pela ação Parar da notificação (o botão não ficou visível na aba)"
        }
        assertTrue("o alerta parou", esperar { tocando == null })
        assertTrue("a sirene parou", naPrincipal { !g.sirene.tocando() })
        nota = "Alerta parou $como."
    }

    @Test
    fun c06_varias_mensagens_um_alarme_so() {
        configurar()
        postar(601, "Varias_msg601")
        postar(602, "Varias_msg602")
        postar(603, "Varias_msg603")
        assertTrue("as três somaram no mesmo alerta", esperar { (tocando?.quantidade ?: 0) == 3 })
        val alertas = g.historico.eventos.value.count { it.tipo == TipoEvento.ALERTA && it.t >= inicioDoTeste }
        assertEquals("um alerta só no histórico", 1, alertas)
        nota = "Três mensagens seguidas viraram um alerta só, com contador 3."
    }

    @Test
    fun c07_tela_bloqueada_mostra_o_alerta() {
        configurar()
        device.sleep()
        Thread.sleep(2_000)
        val bloqueado = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        postar(701, "Bloqueado_msg701")
        assertTrue(esperar { tocando != null })
        val parar = device.wait(Until.findObject(By.desc("Parar o alerta")), 15_000)
        assertNotNull("a tela do alerta apareceu", parar)
        val acesa = app.getSystemService(PowerManager::class.java).isInteractive
        foto("c07_alerta_sobre_a_tela_bloqueada")
        parar!!.click()
        assertTrue("o botão Parar da tela do alerta funcionou", esperar { tocando == null })
        nota = "Tela apagada e bloqueada (bloqueio=$bloqueado): a tela do alerta abriu por cima, tela acesa=$acesa; Parar funcionou."
    }

    @Test
    fun c07b_botao_de_volume_para() {
        configurar()
        device.sleep()
        Thread.sleep(2_000)
        postar(702, "Volume_msg702")
        assertNotNull("a tela do alerta apareceu", device.wait(Until.findObject(By.desc("Parar o alerta")), 15_000))
        device.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertTrue("o botão de volume parou o alerta", esperar { tocando == null })
        nota = "Com a tela do alerta aberta, o botão de volume parou o alerta."
    }

    @Test
    fun c09_escuta_desligada_e_religada() {
        configurar()
        sh("cmd notification disallow_listener $servico")
        assertTrue("a escuta desconectou", esperar { !SentinelaService.conectada.value })
        val diag = Diagnostico.verificar(app, g)
        assertEquals(Gravidade.ERRO, diag.first { it.titulo == "Acesso às notificações" }.gravidade)
        naPrincipal { g.coordenador.sincronizar("teste") }
        val nm = app.getSystemService(NotificationManager::class.java)
        assertTrue("aviso de problema publicado", esperar { nm.activeNotifications.any { it.id == Notificador.ID_PROBLEMA } })
        postar(901, "SemEscuta_msg901")
        Thread.sleep(2_500)
        assertNull("sem acesso, o app não percebe a mensagem", tocando)
        sh("cmd notification allow_listener $servico")
        assertTrue("a escuta voltou", esperar(20_000) { SentinelaService.conectada.value })
        postar(902, "Voltou_msg902")
        assertTrue("depois de religar, volta a tocar", esperar { tocando != null })
        nota = "Sem acesso às notificações: diagnóstico mostrou erro e o app publicou o aviso de problema; ao religar, voltou a tocar."
    }

    @Test
    fun c10_recalcula_depois_de_reiniciar() {
        val zona = ZoneId.systemDefault()
        val agora = System.currentTimeMillis()
        val daquiA2Min = Instant.ofEpochMilli(agora + 2 * MIN).atZone(zona)
        val p = Plantao(
            id = "reinicio", nome = "Plantão do teste", tipo = TipoAgenda.SEMANAL,
            dias = setOf(daquiA2Min.dayOfWeek.value),
            inicioMin = daquiA2Min.hour * 60 + daquiA2Min.minute,
            fimMin = (daquiA2Min.hour * 60 + daquiA2Min.minute + 60) % (24 * 60)
        )
        configurar(plantaoAgora = false)
        naPrincipal { g.repo.atualizar { it.copy(plantoes = listOf(p)) } }
        // Simula o reinício: o Android apaga os alarmes do app.
        naPrincipal { g.agendador.agendarTransicao(null) }
        app.sendBroadcast(Intent(app, SistemaReceiver::class.java).setAction("com.plantaoseguro.TESTE_REINICIO"))
        val esperado = Agenda.proximaTransicao(listOf(p), System.currentTimeMillis(), zona, emptyList())
        val am = app.getSystemService(AlarmManager::class.java)
        assertTrue(
            "o próximo início foi reagendado no relógio do sistema",
            esperar { am.nextAlarmClock?.let { it.triggerTime == esperado && it.showIntent?.creatorPackage == pkg } == true }
        )
        nota = "Com os alarmes apagados, o receptor de sistema reagendou o início do plantão (${Instant.ofEpochMilli(esperado!!)})."
    }

    @Test
    fun c12_trocar_o_som_da_regra() {
        configurar(regraBase.copy(toque = regraBase.toque.copy(som = SomRef.Embutido("sirene_rapida"))))
        postar(1201, "Som_msg1201")
        assertTrue(esperar { tocando != null })
        assertEquals(SomRef.Embutido("sirene_rapida"), tocando!!.toque.som)
        assertTrue(naPrincipal { g.sirene.tocando() })
        nota = "A regra com a Sirene rápida tocou com esse som."
    }

    @Test
    fun c13_som_de_arquivo_e_arquivo_apagado() {
        val dir = File(app.cacheDir, "exportacoes").apply { mkdirs() }
        val f = File(dir, "meu_som.ogg")
        app.resources.openRawResource(R.raw.gota).use { i -> f.outputStream().use { i.copyTo(it) } }
        val uri = FileProvider.getUriForFile(app, "$pkg.arquivos", f)
        configurar(regraBase.copy(toque = regraBase.toque.copy(som = SomRef.Arquivo(uri.toString(), "Meu som"))))
        postar(1301, "Arquivo_msg1301")
        assertTrue(esperar { tocando != null })
        assertTrue("tocou o som do arquivo", naPrincipal { g.sirene.tocando() })
        naPrincipal { g.central.confirmar("teste") }
        f.delete()
        postar(1302, "Arquivo_msg1302")
        assertTrue("com o arquivo apagado, ainda toca", esperar { tocando != null })
        assertTrue("toca o som padrão no lugar", naPrincipal { g.sirene.tocando() })
        nota = "Som de um arquivo (content://) tocou; depois de apagar o arquivo, o alerta tocou com o som padrão."
    }

    @Test
    fun c14_silencioso_e_nao_perturbe() {
        val audio = app.getSystemService(AudioManager::class.java)
        val nm = app.getSystemService(NotificationManager::class.java)
        configurar(plantaoAgora = false)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        Thread.sleep(1_000)
        val antesFiltro = nm.currentInterruptionFilter
        val antesModo = audio.ringerMode
        try {
            naPrincipal { g.coordenador.comecarAgora(60) }
            assertTrue("saiu do silencioso", esperar { audio.ringerMode == AudioManager.RINGER_MODE_NORMAL })
            assertTrue("desligou o Não Perturbe", esperar { nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL })
            postar(1401, "Silencio_msg1401")
            assertTrue("o alerta tocou", esperar { tocando != null })
            naPrincipal { g.central.confirmar("teste") }
            naPrincipal { g.coordenador.encerrarAgora() }
            assertTrue("o Não Perturbe voltou", esperar { nm.currentInterruptionFilter == antesFiltro })
            assertTrue("o modo de som voltou", esperar { audio.ringerMode == antesModo })
            nota = "Antes: silencioso e Não Perturbe ($antesFiltro). No plantão: som normal e Não Perturbe desligado. No fim: tudo como antes."
        } finally {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
    }

    // ---------------------------------------------------------------- outros comportamentos

    @Test
    fun c16_sem_confirmacao_toca_de_novo() {
        configurar(regraBase.copy(toque = regraBase.toque.copy(duracaoSeg = 5, insistirMin = 1)))
        postar(1601, "Insiste_msg1601")
        assertTrue(esperar { tocando != null })
        assertTrue("parou sozinho e ficou aguardando confirmação", esperar(20_000) { g.central.pendente.value != null })
        assertTrue("tocou de novo depois de 1 minuto", esperar(100_000) { (tocando?.insistencias ?: 0) >= 1 })
        cancelar(1601)
        assertTrue("abrir a mensagem encerrou", esperar { tocando == null && g.central.pendente.value == null })
        nota = "Tocou 5 s, ficou sem confirmação, voltou a tocar 1 min depois pelo AlarmManager; fechar a mensagem encerrou."
    }

    @Test
    fun c17_chamada_para_quando_encerrada() {
        configurar(ajustes = Ajustes(boasVindasConcluida = true, pararAoTocarNaMensagem = false))
        postar(1701, "Chamada_msg1701", chamada = true)
        assertTrue("chamada tocou como chamada", esperar { tocando?.chamada == true })
        cancelar(1701)
        assertTrue("chamada encerrada parou o alerta", esperar { tocando == null })
        nota = "Chamada (categoria call com tela cheia) tocou e parou quando o app fechou a notificação, mesmo com o ajuste de parar desligado."
    }

    @Test
    fun c18_ajuste_desligado_continua_tocando() {
        configurar(ajustes = Ajustes(boasVindasConcluida = true, pararAoTocarNaMensagem = false))
        postar(1801, "Ajuste_msg1801")
        assertTrue(esperar { tocando != null })
        cancelar(1801)
        Thread.sleep(2_500)
        assertNotNull("com o ajuste desligado, fechar a mensagem não para", tocando)
        nota = "Com \"parar ao tocar na mensagem\" desligado, o alerta continuou até alguém parar."
    }

    @Test
    fun c19_filtro_por_palavra() {
        configurar(regraBase.copy(palavras = listOf("urgente")))
        postar(1901, "Rotina_de_sempre_msg1901")
        Thread.sleep(2_500)
        assertNull("sem a palavra, não toca", tocando)
        postar(1902, "Paciente_URGENTE_msg1902")
        assertTrue("com a palavra, toca", esperar { tocando != null })
        nota = "Regra com a palavra \"urgente\": rotina não tocou; \"URGENTE\" tocou."
    }

    @Test
    fun c20_regra_silenciada_por_um_tempo() {
        configurar()
        naPrincipal { g.coordenador.silenciarRegra("e2e", 60) }
        postar(2001, "Silenciada_msg2001")
        Thread.sleep(2_500)
        assertNull("regra silenciada não toca", tocando)
        naPrincipal { g.coordenador.silenciarRegra("e2e", 0) }
        postar(2002, "Reativada_msg2002")
        assertTrue("depois de reativar, toca", esperar { tocando != null })
        nota = "Silenciada por 1 h não tocou; reativada tocou."
    }

    @Test
    fun c21_teste_da_escuta_do_diagnostico() {
        configurar(plantaoAgora = false)
        val ms = naPrincipal { Diagnostico.testarEscuta(g) }
        assertNotNull("a escuta percebeu a notificação de teste", ms)
        nota = "Teste ponta a ponta do diagnóstico: a escuta percebeu em $ms ms."
    }

    @Test
    fun c22_teste_com_tela_bloqueada() {
        configurar(plantaoAgora = false)
        naPrincipal { g.coordenador.testarComTelaBloqueada(3) }
        device.sleep()
        val parar = device.wait(Until.findObject(By.desc("Parar o alerta")), 20_000)
        assertNotNull("a tela do teste apareceu", parar)
        assertTrue(tocando?.teste == true)
        foto("c22_teste_com_tela_bloqueada")
        parar!!.click()
        assertTrue(esperar { tocando == null })
        nota = "O botão \"Testar bloqueado\" tocou o alerta 3 s depois, por cima da tela bloqueada."
    }

    @Test
    fun c23_abrir_a_conversa_pelo_alerta() {
        configurar()
        postar(2301, "Abrir_msg2301")
        assertTrue(esperar { tocando != null })
        device.openNotification()
        val botao = device.wait(Until.findObject(By.text(Pattern.compile("Abrir conversa", Pattern.CASE_INSENSITIVE))), 8_000)
        val como = if (botao != null) {
            botao.click()
            "pelo botão da notificação"
        } else {
            val nm = app.getSystemService(NotificationManager::class.java)
            val n = nm.activeNotifications.first { it.id == Notificador.ID_ALERTA }.notification
            n.actions.first { it.title.toString() == "Abrir conversa" }.actionIntent.send()
            "pela ação da notificação"
        }
        assertTrue("o alerta parou", esperar { tocando == null })
        assertTrue("a conversa de origem abriu", device.wait(Until.hasObject(By.textStartsWith("Conversa aberta")), 10_000))
        nota = "\"Abrir conversa\" ($como) parou o alerta e abriu a conversa usando o atalho da notificação original."
    }
}
