package com.plantaoseguro.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatoTest {

    private val sp = ZoneId.of("America/Sao_Paulo")
    private fun ms(dia: Int, hora: Int, min: Int = 0) =
        LocalDateTime.of(2026, 10, dia, hora, min).atZone(sp).toInstant().toEpochMilli()

    @Test
    fun `quando fala como gente`() {
        val agora = ms(13, 23, 40) // terça
        assertEquals("hoje às 23:50", Formato.quando(ms(13, 23, 50), agora, sp))
        assertEquals("amanhã às 07:00", Formato.quando(ms(14, 7), agora, sp))
        assertEquals("ontem às 19:00", Formato.quando(ms(12, 19), agora, sp))
        assertEquals("sexta às 19:00", Formato.quando(ms(16, 19), agora, sp))
        assertEquals("24/10 às 08:00", Formato.quando(ms(24, 8), agora, sp))
        assertEquals("01/10 às 08:00", Formato.quando(ms(1, 8), agora, sp))
    }

    @Test
    fun `duracoes`() {
        assertEquals("45 min", Formato.duracao(45 * 60_000L))
        assertEquals("1 h", Formato.duracao(59 * 60_000L + 30_000L))
        assertEquals("12 h", Formato.duracao(12 * 3_600_000L))
        assertEquals("6 h 12 min", Formato.duracao(6 * 3_600_000L + 12 * 60_000L))
        assertEquals("3 dias", Formato.duracao(72 * 3_600_000L))
        assertEquals("0 min", Formato.duracao(-5_000L))
    }

    @Test
    fun `dias da semana`() {
        assertEquals("Segunda a sexta", Formato.dias(setOf(5, 4, 3, 2, 1)))
        assertEquals("Sábado e domingo", Formato.dias(setOf(6, 7)))
        assertEquals("Todos os dias", Formato.dias((1..7).toSet()))
        assertEquals("Seg, Qua e Sex", Formato.dias(setOf(1, 3, 5)))
        assertEquals("Dom", Formato.dias(setOf(7)))
        assertEquals("Nenhum dia", Formato.dias(emptySet()))
    }

    @Test
    fun `horarios e cabecalhos`() {
        assertEquals("07:05", Formato.horaMin(7 * 60 + 5))
        val agora = ms(13, 23, 40)
        assertEquals("Hoje", Formato.cabecalhoDia(ms(13, 1), agora, sp))
        assertEquals("Ontem", Formato.cabecalhoDia(ms(12, 23), agora, sp))
        assertEquals("Sábado, 10/10", Formato.cabecalhoDia(ms(10, 12), agora, sp))
    }
}
