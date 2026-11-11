package com.plantaoseguro.nucleo

import com.plantaoseguro.agenda.Ocorrencia
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.PKG_WHATSAPP_BUSINESS
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorTest {

    private val agora = 1_760_000_000_000L
    private val hora = 3_600_000L
    private val wa = PKG_WHATSAPP_BUSINESS

    private val turno = Ocorrencia(Plantao(id = "p1", nome = "Noturno"), inicio = agora - hora, fim = agora + 5 * hora)
    private val padrao = Regra.padrao()
    private val ajustes = Ajustes()

    private fun msg(
        chave: String = "0|$wa|1|conversa1|10",
        quando: Long = agora - 1_000,
        pacote: String = wa,
        categoria: String? = "msg",
        conteudo: Conteudo? = null,
        emAndamento: Boolean = false,
        resumo: Boolean = false,
        telaCheia: Boolean = false,
        umaVez: Boolean = false
    ) = Recebida(
        chave = chave, pacote = pacote, quando = quando, postadaEm = agora,
        emAndamento = emAndamento, resumoDeGrupo = resumo, soAlertarUmaVez = umaVez,
        categoria = categoria, telaCheia = telaCheia, conteudo = conteudo
    )

    private fun decidir(
        n: Recebida,
        regras: List<Regra> = listOf(padrao),
        ativas: List<Ocorrencia> = listOf(turno),
        aj: Ajustes = ajustes,
        memoria: Memoria = Memoria()
    ) = Motor.decidir(n, regras, ativas, aj, memoria, agora)

    private fun motivo(d: Decisao) = (d as? Decisao.Ignorar)?.motivo

    @Test
    fun `mensagem nova durante o plantao toca`() {
        val d = decidir(msg())
        assertTrue(d is Decisao.Alertar)
        d as Decisao.Alertar
        assertEquals(padrao.id, d.regra.id)
        assertFalse(d.chamada)
        assertEquals("p1", d.plantao?.plantao?.id)
    }

    @Test
    fun `app que nenhuma regra vigia e ignorado`() {
        assertEquals(MotivoIgnorar.NENHUMA_REGRA, motivo(decidir(msg(pacote = "com.whatsapp"))))
        assertEquals(MotivoIgnorar.NENHUMA_REGRA, motivo(decidir(msg(), regras = listOf(padrao.copy(ligada = false)))))
    }

    @Test
    fun `regra silenciada nao toca e volta depois do prazo`() {
        val silenciada = padrao.copy(silenciadaAte = agora + hora)
        assertEquals(MotivoIgnorar.REGRA_SILENCIADA, motivo(decidir(msg(), regras = listOf(silenciada))))
        val vencida = padrao.copy(silenciadaAte = agora - 1)
        assertTrue(decidir(msg(), regras = listOf(vencida)) is Decisao.Alertar)
    }

    @Test
    fun `fora do plantao nao toca mas regra sempre toca`() {
        assertEquals(MotivoIgnorar.FORA_DO_PLANTAO, motivo(decidir(msg(), ativas = emptyList())))
        val sempre = padrao.copy(quando = QuandoVale.SEMPRE)
        val d = decidir(msg(), regras = listOf(sempre), ativas = emptyList())
        assertTrue(d is Decisao.Alertar)
        assertNull((d as Decisao.Alertar).plantao)
    }

    @Test
    fun `plantao sem alertas nao conta`() {
        val semSirene = turno.copy(plantao = turno.plantao.copy(tocarSirene = false))
        assertEquals(MotivoIgnorar.FORA_DO_PLANTAO, motivo(decidir(msg(), ativas = listOf(semSirene))))
    }

    @Test
    fun `regra limitada a certos plantoes`() {
        val soOutro = padrao.copy(plantoes = setOf("p2"))
        assertEquals(MotivoIgnorar.FORA_DO_PLANTAO, motivo(decidir(msg(), regras = listOf(soOutro))))
        val soEste = padrao.copy(plantoes = setOf("p1"))
        assertTrue(decidir(msg(), regras = listOf(soEste)) is Decisao.Alertar)
    }

    @Test
    fun `resumo de grupo e notificacoes em andamento nao sao mensagens`() {
        assertEquals(MotivoIgnorar.NAO_E_MENSAGEM, motivo(decidir(msg(resumo = true))))
        assertEquals(MotivoIgnorar.NAO_E_MENSAGEM, motivo(decidir(msg(emAndamento = true))))
        assertEquals(MotivoIgnorar.NAO_E_MENSAGEM, motivo(decidir(msg(categoria = "progress"))))
    }

    @Test
    fun `atualizacao da mesma notificacao nao toca de novo mas mensagem nova toca`() {
        val memoria = Memoria()
        assertTrue(decidir(msg(), memoria = memoria) is Decisao.Alertar)
        // O app republica a mesma notificação sem mensagem nova (mesmo horário).
        assertEquals(MotivoIgnorar.ATUALIZACAO, motivo(decidir(msg(), memoria = memoria)))
        // Mensagem nova na mesma conversa: mesma chave, horário novo.
        assertTrue(decidir(msg(quando = agora), memoria = memoria) is Decisao.Alertar)
        // Atualização marcada como "alertar só uma vez", mesmo com horário novo.
        assertEquals(MotivoIgnorar.ATUALIZACAO, motivo(decidir(msg(quando = agora + 10, umaVez = true), memoria = memoria)))
    }

    @Test
    fun `mensagem confirmada nao volta a tocar`() {
        val memoria = Memoria()
        val n = msg()
        assertTrue(decidir(n, memoria = memoria) is Decisao.Alertar)
        memoria.confirmar(n.chave, n.quando)
        memoria.esquecer(n.chave) // a notificação saiu da tela...
        // ...e o app a publicou de novo igual (por exemplo, ao sincronizar).
        assertEquals(MotivoIgnorar.JA_CONFIRMADA, motivo(decidir(n, memoria = memoria)))
    }

    @Test
    fun `mensagem antiga nunca toca`() {
        assertEquals(MotivoIgnorar.ANTIGA, motivo(decidir(msg(quando = agora - 13 * hora))))
        // Sem horário: usa o momento em que foi postada.
        assertTrue(decidir(msg(quando = 0L)) is Decisao.Alertar)
    }

    @Test
    fun `mensagem de antes do plantao comecar e ignorada se o ajuste estiver ligado`() {
        val antes = msg(quando = turno.inicio - 5 * 60_000L)
        assertEquals(MotivoIgnorar.ANTIGA, motivo(decidir(antes)))
        assertTrue(decidir(antes, aj = ajustes.copy(ignorarAnteriores = false)) is Decisao.Alertar)
        // Diferença pequena de relógio é tolerada.
        assertTrue(decidir(msg(quando = turno.inicio - 30_000L)) is Decisao.Alertar)
    }

    @Test
    fun `chamada de voz em tela cheia toca como chamada`() {
        val memoria = Memoria()
        val chamada = msg(chave = "call-1", categoria = "call", telaCheia = true, quando = turno.inicio - 2 * hora)
        val d = decidir(chamada, memoria = memoria)
        assertTrue(d is Decisao.Alertar)
        assertTrue((d as Decisao.Alertar).chamada)
        // A notificação da chamada é atualizada enquanto toca: não dispara de novo.
        assertEquals(MotivoIgnorar.ATUALIZACAO, motivo(decidir(chamada.copy(quando = agora), memoria = memoria)))
    }

    @Test
    fun `regra pode ignorar chamadas`() {
        val semChamadas = padrao.copy(chamadas = false)
        val chamada = msg(chave = "call-1", categoria = "call", telaCheia = true)
        assertEquals(MotivoIgnorar.CHAMADA_DESLIGADA, motivo(decidir(chamada, regras = listOf(semChamadas))))
    }

    @Test
    fun `chamada em andamento nao toca e chamada perdida toca como mensagem`() {
        assertEquals(MotivoIgnorar.NAO_E_MENSAGEM, motivo(decidir(msg(categoria = "call", emAndamento = true))))
        val perdida = decidir(msg(chave = "perdida", categoria = "call"))
        assertTrue(perdida is Decisao.Alertar)
        assertFalse((perdida as Decisao.Alertar).chamada)
    }

    @Test
    fun `filtro por contato ou grupo`() {
        val regra = padrao.copy(contatos = listOf("Plantão UTI"))
        val doGrupo = Conteudo(titulo = "PLANTAO UTI ADULTO: Ana", conversa = "PLANTAO UTI ADULTO", texto = "Oi")
        assertTrue(decidir(msg(conteudo = doGrupo), regras = listOf(regra)) is Decisao.Alertar)
        val outro = Conteudo(titulo = "Família", texto = "Oi")
        assertEquals(MotivoIgnorar.FILTRO, motivo(decidir(msg(conteudo = outro), regras = listOf(regra))))
        // Conteúdo escondido (ex.: notificação sem texto): não dá para conferir o filtro.
        assertEquals(MotivoIgnorar.FILTRO, motivo(decidir(msg(conteudo = null), regras = listOf(regra))))
    }

    @Test
    fun `palavras obrigatorias e excecoes`() {
        val regra = padrao.copy(palavras = listOf("urgente", "código azul"), ignorar = listOf("bom dia"))
        fun c(texto: String) = Conteudo(titulo = "Equipe", texto = texto)
        assertTrue(decidir(msg(conteudo = c("Paciente urgente no 12")), regras = listOf(regra)) is Decisao.Alertar)
        assertTrue(decidir(msg(conteudo = c("CODIGO AZUL na sala 3")), regras = listOf(regra)) is Decisao.Alertar)
        assertEquals(MotivoIgnorar.FILTRO, motivo(decidir(msg(conteudo = c("Escala de amanhã")), regras = listOf(regra))))
        assertEquals(MotivoIgnorar.EXCECAO, motivo(decidir(msg(conteudo = c("Bom dia! Algo urgente?")), regras = listOf(regra))))
    }

    @Test
    fun `regra critica tem prioridade e as outras servem de reserva`() {
        val geral = padrao
        val chefia = Regra(id = "chefia", nome = "Chefia", contatos = listOf("Chefia"), critica = true)
        val regras = listOf(geral, chefia)
        val daChefia = decidir(msg(conteudo = Conteudo(titulo = "Chefia de Plantão", texto = "Venha")), regras = regras)
        assertEquals("chefia", (daChefia as Decisao.Alertar).regra.id)
        val deOutro = decidir(msg(conteudo = Conteudo(titulo = "Colega", texto = "Oi")), regras = regras)
        assertEquals(padrao.id, (deOutro as Decisao.Alertar).regra.id)
    }

    @Test
    fun `quando uma regra nao serve a proxima e testada`() {
        val soUrgente = Regra(id = "u", nome = "Só urgente", palavras = listOf("urgente"))
        val sempre = Regra(id = "s", nome = "Sempre", quando = QuandoVale.SEMPRE)
        val d = decidir(msg(conteudo = Conteudo(texto = "rotina")), regras = listOf(soUrgente, sempre), ativas = emptyList())
        assertEquals("s", (d as Decisao.Alertar).regra.id)
    }

    @Test
    fun `texto so e lido quando alguma regra precisa`() {
        assertFalse(Motor.precisaConteudo(listOf(padrao), wa, agora))
        val comFiltro = padrao.copy(palavras = listOf("urgente"))
        assertTrue(Motor.precisaConteudo(listOf(comFiltro), wa, agora))
        assertFalse(Motor.precisaConteudo(listOf(comFiltro), "com.outro.app", agora))
        assertFalse(Motor.precisaConteudo(listOf(comFiltro.copy(silenciadaAte = agora + hora)), wa, agora))
        assertTrue(Motor.monitora(listOf(padrao), wa))
        assertFalse(Motor.monitora(listOf(padrao.copy(ligada = false)), wa))
    }

    @Test
    fun `memoria esquece as mais antigas quando enche`() {
        val m = Memoria(capacidade = 2)
        m.registrar("a", 1)
        m.registrar("b", 2)
        m.registrar("c", 3)
        assertNull(m.quandoVisto("a"))
        assertEquals(3L, m.quandoVisto("c"))
    }
}
