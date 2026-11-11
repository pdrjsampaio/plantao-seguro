package com.plantaoseguro.agenda

import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Pulo
import com.plantaoseguro.modelo.TipoAgenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AgendaTest {

    private val sp: ZoneId = ZoneId.of("America/Sao_Paulo")

    /** 2026-10-12 é uma segunda-feira. */
    private fun ms(dia: Int, hora: Int, min: Int = 0, mes: Int = 10, zona: ZoneId = sp): Long =
        LocalDateTime.of(2026, mes, dia, hora, min).atZone(zona).toInstant().toEpochMilli()

    private val noturno = Plantao(
        id = "noite", nome = "Noturno", tipo = TipoAgenda.SEMANAL,
        dias = setOf(1, 2, 3, 4, 5), inicioMin = 19 * 60, fimMin = 7 * 60
    )

    private val diurno = Plantao(
        id = "dia", nome = "Expediente", tipo = TipoAgenda.SEMANAL,
        dias = setOf(1, 2, 3, 4, 5), inicioMin = 7 * 60 + 50, fimMin = 17 * 60
    )

    @Test
    fun `plantao noturno vale de madrugada e termina no dia seguinte`() {
        // Terça 03:00: vale a ocorrência que começou segunda 19:00.
        val ativas = Agenda.ativas(listOf(noturno), ms(13, 3), sp, emptyList())
        assertEquals(1, ativas.size)
        assertEquals(ms(12, 19), ativas[0].inicio)
        assertEquals(ms(13, 7), ativas[0].fim)
        assertTrue(Agenda.terminaNoDiaSeguinte(noturno))
        assertEquals(12 * 60, Agenda.duracaoMin(noturno))
    }

    @Test
    fun `sexta a noite avanca para o sabado mas sabado a noite nao vale`() {
        assertEquals(1, Agenda.ativas(listOf(noturno), ms(17, 3), sp, emptyList()).size) // sábado 03:00
        assertEquals(0, Agenda.ativas(listOf(noturno), ms(17, 20), sp, emptyList()).size) // sábado 20:00
        assertEquals(0, Agenda.ativas(listOf(noturno), ms(18, 3), sp, emptyList()).size) // domingo 03:00
    }

    @Test
    fun `inicio conta e fim nao conta`() {
        assertEquals(1, Agenda.ativas(listOf(diurno), ms(14, 7, 50), sp, emptyList()).size)
        assertEquals(0, Agenda.ativas(listOf(diurno), ms(14, 7, 49), sp, emptyList()).size)
        assertEquals(0, Agenda.ativas(listOf(diurno), ms(14, 17, 0), sp, emptyList()).size)
        assertEquals(1, Agenda.ativas(listOf(diurno), ms(14, 16, 59), sp, emptyList()).size)
    }

    @Test
    fun `inicio igual ao fim e plantao de 24 horas`() {
        val p = Plantao(id = "24h", nome = "24 horas", dias = setOf(3), inicioMin = 8 * 60, fimMin = 8 * 60)
        assertEquals(24 * 60, Agenda.duracaoMin(p))
        val ativas = Agenda.ativas(listOf(p), ms(15, 7, 59), sp, emptyList()) // quinta 07:59
        assertEquals(1, ativas.size)
        assertEquals(ms(14, 8), ativas[0].inicio)
        assertEquals(ms(15, 8), ativas[0].fim)
    }

    @Test
    fun `plantao desligado ou sem dias nunca vale`() {
        assertTrue(Agenda.ativas(listOf(noturno.copy(ligado = false)), ms(13, 3), sp, emptyList()).isEmpty())
        assertTrue(Agenda.ativas(listOf(noturno.copy(dias = emptySet())), ms(13, 3), sp, emptyList()).isEmpty())
        assertNull(Agenda.proximaTransicao(listOf(noturno.copy(ligado = false)), ms(13, 3), sp, emptyList()))
    }

    @Test
    fun `folga pula so aquela ocorrencia`() {
        val pulos = listOf(Pulo("noite", ms(13, 19)))
        assertEquals(0, Agenda.ativas(listOf(noturno), ms(13, 23), sp, pulos).size) // terça à noite: folga
        assertEquals(1, Agenda.ativas(listOf(noturno), ms(14, 23), sp, pulos).size) // quarta volta
        // A próxima ocorrência a partir de terça 08:00 passa a ser a de quarta.
        val prox = Agenda.proxima(listOf(noturno), ms(13, 8), sp, pulos)
        assertEquals(ms(14, 19), prox?.inicio)
    }

    @Test
    fun `proxima transicao e o proximo inicio ou fim`() {
        val plantoes = listOf(noturno, diurno)
        assertEquals(ms(13, 7), Agenda.proximaTransicao(plantoes, ms(13, 3), sp, emptyList()))
        assertEquals(ms(13, 7, 50), Agenda.proximaTransicao(plantoes, ms(13, 7), sp, emptyList()))
        assertEquals(ms(13, 17), Agenda.proximaTransicao(plantoes, ms(13, 12), sp, emptyList()))
        assertEquals(ms(13, 19), Agenda.proximaTransicao(plantoes, ms(13, 17), sp, emptyList()))
        // Sábado de manhã: só segunda.
        assertEquals(ms(17, 7), Agenda.proximaTransicao(plantoes, ms(17, 6), sp, emptyList()))
        assertEquals(ms(19, 7, 50), Agenda.proximaTransicao(plantoes, ms(17, 8), sp, emptyList()))
    }

    @Test
    fun `plantao unico ativo futuro e encerrado`() {
        val p = Plantao(id = "u", nome = "Cobertura", tipo = TipoAgenda.UNICO, inicioMs = ms(20, 10), fimMs = ms(21, 10))
        assertTrue(Agenda.ativas(listOf(p), ms(20, 9), sp, emptyList()).isEmpty())
        assertEquals(1, Agenda.ativas(listOf(p), ms(20, 22), sp, emptyList()).size)
        assertEquals(ms(20, 10), Agenda.proxima(listOf(p), ms(14, 0), sp, emptyList())?.inicio)
        assertEquals(ms(20, 10), Agenda.proximaTransicao(listOf(p), ms(14, 0), sp, emptyList()))
        // "Encerrar agora" põe o fim no instante atual.
        val encerrado = p.copy(fimMs = ms(20, 22))
        assertTrue(Agenda.ativas(listOf(encerrado), ms(20, 22), sp, emptyList()).isEmpty())
    }

    @Test
    fun `plantao unico daqui a um mes ainda aparece como proximo`() {
        val p = Plantao(id = "u", nome = "Mutirão", tipo = TipoAgenda.UNICO, inicioMs = ms(14, 8, mes = 11), fimMs = ms(14, 18, mes = 11))
        assertEquals(p.inicioMs, Agenda.proxima(listOf(p), ms(14, 0), sp, emptyList())?.inicio)
        assertEquals(p.inicioMs, Agenda.proximaTransicao(listOf(p), ms(14, 0), sp, emptyList()))
    }

    @Test
    fun `plantao unico com fim antes do inicio e ignorado`() {
        val p = Plantao(id = "u", nome = "Errado", tipo = TipoAgenda.UNICO, inicioMs = ms(20, 10), fimMs = ms(20, 9))
        assertNull(Agenda.proxima(listOf(p), ms(14, 0), sp, emptyList()))
        assertTrue(Agenda.ativas(listOf(p), ms(20, 9, 30), sp, emptyList()).isEmpty())
    }

    @Test
    fun `dois plantoes ao mesmo tempo vem ordenados pelo fim`() {
        val extra = Plantao(id = "x", nome = "Extra", dias = setOf(2), inicioMin = 20 * 60, fimMin = 23 * 60)
        val ativas = Agenda.ativas(listOf(noturno, extra), ms(13, 21), sp, emptyList())
        assertEquals(listOf("x", "noite"), ativas.map { it.plantao.id })
    }

    @Test
    fun `escala do dia inclui o plantao que vem da noite anterior`() {
        val terca = LocalDate.of(2026, 10, 13)
        val doDia = Agenda.doDia(listOf(noturno), terca, sp, emptyList())
        assertEquals(listOf(ms(12, 19), ms(13, 19)), doDia.map { it.inicio }.sorted())
    }

    @Test
    fun `horario de verao nao quebra o calculo`() {
        // Nova York adianta o relógio em 08/03/2026 às 02:00.
        val ny = ZoneId.of("America/New_York")
        val p = Plantao(id = "n", nome = "Noite", dias = setOf(6), inicioMin = 22 * 60, fimMin = 6 * 60)
        val agora = LocalDateTime.of(2026, 3, 8, 4, 0).atZone(ny).toInstant().toEpochMilli()
        val ativas = Agenda.ativas(listOf(p), agora, ny, emptyList())
        assertEquals(1, ativas.size)
        // Noite com uma hora a menos: 7 h em vez de 8 h.
        assertEquals(7L * 3_600_000L, ativas[0].fim - ativas[0].inicio)
    }

    // ------------------------------------------------------------ lembrete do ponto

    private val comPonto = noturno.copy(lembrarPonto = true, pontoAntesMin = 10)

    @Test
    fun `lembrete de ponto na entrada com antecedencia e na saida`() {
        val l = Agenda.lembretesPonto(listOf(comPonto), ms(13, 0), ms(14, 0), sp, emptyList())
        // Terça: saída às 07:00 (do plantão de segunda) e entrada às 18:50.
        assertEquals(
            listOf(ms(13, 7) to MomentoPonto.SAIDA, ms(13, 18, 50) to MomentoPonto.ENTRADA),
            l.map { it.quando to it.momento }
        )
    }

    @Test
    fun `sem aviso de saida quando desligado e nada em plantao sem ponto`() {
        val semSaida = comPonto.copy(pontoSaida = false)
        val l = Agenda.lembretesPonto(listOf(semSaida, diurno), ms(13, 0), ms(14, 0), sp, emptyList())
        assertEquals(listOf(ms(13, 18, 50)), l.map { it.quando })
    }

    @Test
    fun `folga nao tem lembrete de ponto`() {
        val pulos = listOf(Pulo("noite", ms(13, 19)))
        assertTrue(Agenda.lembretesPonto(listOf(comPonto), ms(13, 12), ms(14, 12), sp, pulos).isEmpty())
    }

    @Test
    fun `proximo lembrete de ponto`() {
        assertEquals(ms(13, 18, 50), Agenda.proximoLembretePonto(listOf(comPonto), ms(13, 8), sp, emptyList()))
        // No exato horário de um aviso, o próximo é o seguinte.
        assertEquals(ms(14, 7), Agenda.proximoLembretePonto(listOf(comPonto), ms(13, 18, 50), sp, emptyList()))
        // Plantão de uma vez daqui a um mês.
        val mutirao = Plantao(
            id = "m", nome = "Mutirão", tipo = TipoAgenda.UNICO, inicioMs = ms(14, 8, mes = 11), fimMs = ms(14, 18, mes = 11),
            lembrarPonto = true, pontoAntesMin = 0
        )
        assertEquals(mutirao.inicioMs, Agenda.proximoLembretePonto(listOf(mutirao), ms(14, 0), sp, emptyList()))
        assertNull(Agenda.proximoLembretePonto(listOf(noturno), ms(13, 8), sp, emptyList()))
    }

    @Test
    fun `comecar agora nao lembra de ponto`() {
        val rapido = Plantao(
            id = "r", nome = "Agora", tipo = TipoAgenda.UNICO, inicioMs = ms(13, 10), fimMs = ms(13, 22),
            rapido = true, lembrarPonto = true
        )
        assertTrue(Agenda.lembretesPonto(listOf(rapido), ms(13, 0), ms(14, 0), sp, emptyList()).isEmpty())
    }
}
