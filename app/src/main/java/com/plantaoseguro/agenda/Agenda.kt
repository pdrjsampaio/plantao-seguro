package com.plantaoseguro.agenda

import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Pulo
import com.plantaoseguro.modelo.TipoAgenda
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Uma vez concreta em que um plantão acontece, em milissegundos. */
data class Ocorrencia(val plantao: Plantao, val inicio: Long, val fim: Long) {
    val chave: String get() = "${plantao.id}@$inicio"
}

/**
 * Cálculo puro da agenda: dado o relógio, diz quais plantões estão valendo e
 * quando a situação muda. Nada aqui guarda estado — por isso um alarme
 * perdido, um reinício ou uma troca de fuso não deixam o app "travado" num
 * plantão errado: na próxima vez que alguém pergunta, a resposta é recalculada.
 */
object Agenda {

    private const val DIA_MS = 24L * 60 * 60 * 1000

    /** Duração do plantão semanal em minutos (fim <= início vira o dia seguinte). */
    fun duracaoMin(p: Plantao): Int {
        if (p.tipo == TipoAgenda.UNICO) return ((p.fimMs - p.inicioMs) / 60_000L).toInt()
        val d = p.fimMin - p.inicioMin
        return if (d > 0) d else d + 24 * 60
    }

    fun terminaNoDiaSeguinte(p: Plantao): Boolean =
        p.tipo == TipoAgenda.SEMANAL && p.fimMin <= p.inicioMin

    /** Todas as ocorrências que encostam no intervalo [de, ate). */
    fun ocorrencias(p: Plantao, de: Long, ate: Long, zona: ZoneId): List<Ocorrencia> {
        if (!p.ligado) return emptyList()
        return when (p.tipo) {
            TipoAgenda.UNICO ->
                if (p.fimMs > p.inicioMs && p.inicioMs < ate && p.fimMs > de) {
                    listOf(Ocorrencia(p, p.inicioMs, p.fimMs))
                } else emptyList()

            TipoAgenda.SEMANAL -> {
                if (p.dias.isEmpty()) return emptyList()
                val primeiroDia = Instant.ofEpochMilli(de).atZone(zona).toLocalDate().minusDays(1)
                val ultimoDia = Instant.ofEpochMilli(ate).atZone(zona).toLocalDate()
                val lista = ArrayList<Ocorrencia>()
                var dia = primeiroDia
                while (!dia.isAfter(ultimoDia)) {
                    if (dia.dayOfWeek.value in p.dias) {
                        val ini = momento(dia, p.inicioMin, zona)
                        val fimDia = if (p.fimMin > p.inicioMin) dia else dia.plusDays(1)
                        val fim = momento(fimDia, p.fimMin, zona)
                        if (fim > ini && ini < ate && fim > de) lista += Ocorrencia(p, ini, fim)
                    }
                    dia = dia.plusDays(1)
                }
                lista
            }
        }
    }

    private fun momento(dia: LocalDate, minutos: Int, zona: ZoneId): Long =
        ZonedDateTime.of(dia.atTime(minutos / 60, minutos % 60), zona).toInstant().toEpochMilli()

    private fun pulada(o: Ocorrencia, pulos: List<Pulo>): Boolean =
        pulos.any { it.plantaoId == o.plantao.id && it.inicioOcorrencia == o.inicio }

    /** Plantões valendo agora (início <= agora < fim), sem os encerrados antes da hora. */
    fun ativas(plantoes: List<Plantao>, agora: Long, zona: ZoneId, pulos: List<Pulo>): List<Ocorrencia> =
        plantoes.flatMap { ocorrencias(it, agora, agora + 1, zona) }
            .filter { it.inicio <= agora && agora < it.fim && !pulada(it, pulos) }
            .sortedBy { it.fim }

    /** O próximo plantão que ainda vai começar. */
    fun proxima(plantoes: List<Plantao>, agora: Long, zona: ZoneId, pulos: List<Pulo>): Ocorrencia? =
        plantoes.flatMap { ocorrencias(it, agora, agora + 8 * DIA_MS, zona) + unicosFuturos(it, agora) }
            .filter { it.inicio > agora && !pulada(it, pulos) }
            .minByOrNull { it.inicio }

    private fun unicosFuturos(p: Plantao, agora: Long): List<Ocorrencia> =
        if (p.ligado && p.tipo == TipoAgenda.UNICO && p.inicioMs > agora && p.fimMs > p.inicioMs) {
            listOf(Ocorrencia(p, p.inicioMs, p.fimMs))
        } else emptyList()

    /** O próximo instante (depois de agora) em que algum plantão começa ou termina. */
    fun proximaTransicao(plantoes: List<Plantao>, agora: Long, zona: ZoneId, pulos: List<Pulo>): Long? {
        val candidatas = plantoes.flatMap {
            ocorrencias(it, agora - DIA_MS, agora + 8 * DIA_MS, zona) + unicosFuturos(it, agora)
        }.filter { !pulada(it, pulos) }
        var melhor: Long? = null
        for (o in candidatas) {
            for (t in longArrayOf(o.inicio, o.fim)) {
                if (t > agora && (melhor == null || t < melhor)) melhor = t
            }
        }
        return melhor
    }

    /** Ocorrências de um dia do calendário, para desenhar a escala da semana. */
    fun doDia(plantoes: List<Plantao>, dia: LocalDate, zona: ZoneId, pulos: List<Pulo>): List<Ocorrencia> {
        val de = dia.atStartOfDay(zona).toInstant().toEpochMilli()
        val ate = dia.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        return plantoes.flatMap { ocorrencias(it, de, ate, zona) }
            .filter { !pulada(it, pulos) }
            .distinctBy { it.chave }
    }
}
