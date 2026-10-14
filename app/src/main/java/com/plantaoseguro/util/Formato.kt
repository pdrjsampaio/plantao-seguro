package com.plantaoseguro.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

/** Textos de data e hora em português do Brasil. */
object Formato {

    val LOCAL: Locale = Locale.forLanguageTag("pt-BR")
    private val fHora = DateTimeFormatter.ofPattern("HH:mm", LOCAL)
    private val fDiaMes = DateTimeFormatter.ofPattern("dd/MM", LOCAL)

    val DIAS_CURTOS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    val DIAS_LETRA = listOf("S", "T", "Q", "Q", "S", "S", "D")
    private val DIAS_LONGOS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

    fun hora(ms: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(zona).format(fHora)

    fun horaMin(minutos: Int): String = String.format(LOCAL, "%02d:%02d", minutos / 60, minutos % 60)

    fun diaMes(ms: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ms).atZone(zona).format(fDiaMes)

    fun diaSemanaLongo(dia: LocalDate): String = DIAS_LONGOS[dia.dayOfWeek.value - 1]

    /** "hoje às 19:00", "amanhã às 07:00", "sexta às 19:00", "24/10 às 08:00". */
    fun quando(ms: Long, agora: Long = System.currentTimeMillis(), zona: ZoneId = ZoneId.systemDefault()): String {
        val dia = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
        val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
        val h = hora(ms, zona)
        return when (val dif = ChronoUnit.DAYS.between(hoje, dia)) {
            0L -> "hoje às $h"
            1L -> "amanhã às $h"
            -1L -> "ontem às $h"
            in 2L..6L -> "${diaSemanaLongo(dia)} às $h"
            else -> if (dif < 0) "${dia.format(fDiaMes)} às $h" else "${dia.format(fDiaMes)} às $h"
        }
    }

    /** "45 min", "6 h", "6 h 12 min", "3 dias". */
    fun duracao(ms: Long): String {
        val min = ceil(ms / 60_000.0).toLong().coerceAtLeast(0)
        return when {
            min < 60 -> "$min min"
            min < 48 * 60 -> {
                val h = min / 60
                val m = min % 60
                if (m == 0L) "$h h" else "$h h $m min"
            }
            else -> "${min / (24 * 60)} dias"
        }
    }

    fun dias(dias: Set<Int>): String {
        val s = dias.sorted()
        return when {
            s.isEmpty() -> "Nenhum dia"
            s.size == 7 -> "Todos os dias"
            s == listOf(1, 2, 3, 4, 5) -> "Segunda a sexta"
            s == listOf(6, 7) -> "Sábado e domingo"
            else -> {
                val nomes = s.map { DIAS_CURTOS[it - 1] }
                if (nomes.size == 1) nomes[0] else nomes.dropLast(1).joinToString(", ") + " e " + nomes.last()
            }
        }
    }

    /** Cabeçalho de dia no histórico: "Hoje", "Ontem", "segunda, 13/10". */
    fun cabecalhoDia(ms: Long, agora: Long = System.currentTimeMillis(), zona: ZoneId = ZoneId.systemDefault()): String {
        val dia = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
        val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
        return when (ChronoUnit.DAYS.between(dia, hoje)) {
            0L -> "Hoje"
            1L -> "Ontem"
            else -> "${diaSemanaLongo(dia).replaceFirstChar { it.uppercase() }}, ${dia.format(fDiaMes)}"
        }
    }
}
