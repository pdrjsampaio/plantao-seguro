package com.plantaoseguro.nucleo

import com.plantaoseguro.agenda.Ocorrencia
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.PKG_WHATSAPP_BUSINESS
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.modelo.Toque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Efeitos de mentira: anotam o que a central pediu, sem som nem Android. */
private class EfeitosFalsos : Efeitos {
    var tocarFunciona = true
    var somTocando = false
    val chamadas = mutableListOf<String>()
    val eventos = mutableListOf<Triple<TipoEvento, Nivel, String>>()
    val insistencias = mutableMapOf<String, Long>()

    override fun tocar(toque: Toque, alerta: AlertaAtivo): Boolean {
        chamadas += "tocar"
        if (tocarFunciona) somTocando = true
        return tocarFunciona
    }
    override fun renovar(toque: Toque) { chamadas += "renovar" }
    override fun parar() { chamadas += "parar"; somTocando = false }
    override fun mostrarAlerta(alerta: AlertaAtivo) { chamadas += "mostrarAlerta" }
    override fun limparAlerta() { chamadas += "limparAlerta" }
    override fun mostrarNaoConfirmado(alerta: AlertaAtivo) { chamadas += "mostrarNaoConfirmado" }
    override fun limparNaoConfirmado() { chamadas += "limparNaoConfirmado" }
    override fun problema(titulo: String, texto: String) { chamadas += "problema" }
    override fun registrar(tipo: TipoEvento, nivel: Nivel, titulo: String, detalhe: String?) { eventos += Triple(tipo, nivel, titulo) }
    override fun agendarInsistencia(chave: String, pacote: String, quando: Long) { insistencias[chave] = quando }
    override fun cancelarInsistencia(chave: String) { insistencias.remove(chave) }

    fun quantas(chamada: String) = chamadas.count { it == chamada }
}

class CentralDeAlertasTest {

    private var relogio = 1_760_000_000_000L
    private val ef = EfeitosFalsos()
    private val central = CentralDeAlertas(ef, Memoria()) { relogio }
    private val wa = PKG_WHATSAPP_BUSINESS
    private val turno = Ocorrencia(Plantao(id = "p1", nome = "Noturno"), relogio - 3_600_000L, relogio + 3_600_000L)

    private fun msg(chave: String, quando: Long = relogio - 1_000) =
        Recebida(chave = chave, pacote = wa, quando = quando, postadaEm = relogio, categoria = "msg")

    private fun alertar(chave: String, regra: Regra = Regra.padrao(), chamada: Boolean = false): Recebida {
        val n = msg(chave)
        central.disparar(Decisao.Alertar(regra, chamada, turno), n, "WhatsApp Business", null)
        return n
    }

    @Test
    fun `alerta toca e fica registrado`() {
        alertar("k1")
        val a = central.tocando.value
        assertNotNull(a)
        assertEquals(1, a!!.quantidade)
        assertEquals("Noturno", a.plantaoNome)
        assertTrue(ef.somTocando)
        assertTrue(ef.eventos.any { it.first == TipoEvento.ALERTA && it.second == Nivel.OK })
    }

    @Test
    fun `um alarme por vez e mensagens novas somam no que ja toca`() {
        alertar("k1")
        alertar("k2")
        val a = central.tocando.value!!
        assertEquals(2, a.quantidade)
        assertEquals(setOf("k1", "k2"), a.chaves.keys)
        assertEquals(1, ef.quantas("tocar"))
        assertEquals(1, ef.quantas("renovar"))
    }

    @Test
    fun `tocar na notificacao original para o alerta`() {
        val n = alertar("k1")
        central.notificacaoRemovida("k1", MotivoRemocao.TOCOU, pararAoTocarNaMensagem = true)
        assertNull(central.tocando.value)
        assertFalse(ef.somTocando)
        assertTrue(central.memoria.confirmada(n.chave, n.quando))
        assertTrue(ef.eventos.any { it.first == TipoEvento.ALERTA_CONFIRMADO })
    }

    @Test
    fun `com o ajuste desligado tocar na mensagem nao para o alerta`() {
        alertar("k1")
        central.notificacaoRemovida("k1", MotivoRemocao.DISPENSOU, pararAoTocarNaMensagem = false)
        assertNotNull(central.tocando.value)
        assertTrue(ef.somTocando)
    }

    @Test
    fun `chamada atendida ou encerrada sempre para o alerta`() {
        alertar("call", chamada = true)
        central.notificacaoRemovida("call", MotivoRemocao.CHAMADA_ENCERRADA, pararAoTocarNaMensagem = false)
        assertNull(central.tocando.value)
    }

    @Test
    fun `remocao sem motivo de leitura ou de outra notificacao nao para`() {
        alertar("k1")
        central.notificacaoRemovida("k1", null, pararAoTocarNaMensagem = true)
        assertNotNull(central.tocando.value)
        central.notificacaoRemovida("outra", MotivoRemocao.TOCOU, pararAoTocarNaMensagem = true)
        assertNotNull(central.tocando.value)
    }

    @Test
    fun `depois de confirmada a mesma mensagem nao toca de novo`() {
        val n = alertar("k1")
        central.confirmar("Você tocou em Parar")
        val d = Motor.decidir(n, listOf(Regra.padrao()), listOf(turno), Ajustes(), central.memoria, relogio)
        assertEquals(MotivoIgnorar.JA_CONFIRMADA, (d as Decisao.Ignorar).motivo)
    }

    @Test
    fun `som que termina sozinho deixa o alerta aguardando confirmacao`() {
        alertar("k1")
        central.tempoEsgotado()
        assertNull(central.tocando.value)
        assertNotNull(central.pendente.value)
        assertEquals(1, ef.quantas("mostrarNaoConfirmado"))
        assertTrue(ef.eventos.any { it.first == TipoEvento.ALERTA_NAO_CONFIRMADO })
        // Abrir a mensagem depois também confirma.
        central.notificacaoRemovida("k1", MotivoRemocao.APP_REMOVEU, pararAoTocarNaMensagem = true)
        assertNull(central.pendente.value)
        assertTrue(ef.eventos.any { it.first == TipoEvento.ALERTA_CONFIRMADO })
    }

    @Test
    fun `insistencia toca de novo enquanto a mensagem continua la`() {
        val regra = Regra.padrao().copy(toque = Toque(insistirMin = 5))
        alertar("k1", regra)
        assertEquals(relogio + 5 * 60_000L, ef.insistencias["k1"])
        central.tempoEsgotado()
        relogio += 5 * 60_000L
        central.insistir("k1", wa, notificacaoAtiva = true)
        val a = central.tocando.value
        assertNotNull(a)
        assertEquals(1, a!!.insistencias)
        assertNull(central.pendente.value)
        assertEquals(2, ef.quantas("tocar"))
        assertEquals(relogio + 5 * 60_000L, ef.insistencias["k1"])
    }

    @Test
    fun `insistencia desiste se a mensagem ja foi aberta`() {
        val regra = Regra.padrao().copy(toque = Toque(insistirMin = 5))
        alertar("k1", regra)
        central.tempoEsgotado()
        central.insistir("k1", wa, notificacaoAtiva = false)
        assertNull(central.pendente.value)
        assertNull(central.tocando.value)
        assertEquals(1, ef.quantas("tocar"))
    }

    @Test
    fun `confirmar cancela as insistencias`() {
        alertar("k1", Regra.padrao().copy(toque = Toque(insistirMin = 5)))
        assertTrue(central.confirmar("Parar"))
        assertTrue(ef.insistencias.isEmpty())
        assertFalse(central.confirmar("de novo"))
    }

    @Test
    fun `alerta novo absorve o que aguardava confirmacao`() {
        alertar("k1")
        central.tempoEsgotado()
        alertar("k2")
        val a = central.tocando.value!!
        assertEquals(setOf("k1", "k2"), a.chaves.keys)
        assertEquals(2, a.quantidade)
        assertNull(central.pendente.value)
    }

    @Test
    fun `teste nao atrapalha alerta de verdade`() {
        assertTrue(central.testar(Toque(), "Mensagens de trabalho"))
        assertTrue(central.tocando.value!!.teste)
        // Mensagem real chega durante o teste: o teste para e o alerta real toca.
        alertar("k1")
        assertFalse(central.tocando.value!!.teste)
        // Teste durante alerta real é recusado.
        assertFalse(central.testar(Toque(), "Mensagens de trabalho"))
        assertFalse(central.tocando.value!!.teste)
    }

    @Test
    fun `teste que toca ate o fim nao vira pendencia`() {
        central.testar(Toque(), "Regra")
        central.tempoEsgotado()
        assertNull(central.tocando.value)
        assertNull(central.pendente.value)
    }

    @Test
    fun `falha ao tocar avisa em vez de fingir que tocou`() {
        ef.tocarFunciona = false
        alertar("k1")
        assertNull(central.tocando.value)
        assertEquals(1, ef.quantas("problema"))
        assertTrue(ef.eventos.any { it.first == TipoEvento.ALERTA && it.second == Nivel.ERRO })
    }

    @Test
    fun `fim do plantao para alertas de plantao mas nao os de sempre`() {
        alertar("k1")
        central.encerrarPorFimDePlantao()
        assertNull(central.tocando.value)

        val sempre = Regra(id = "s", nome = "Sempre", quando = QuandoVale.SEMPRE)
        alertar("k2", sempre)
        central.encerrarPorFimDePlantao()
        assertNotNull(central.tocando.value)
    }
}
