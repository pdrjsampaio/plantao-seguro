package com.plantaoseguro.ui.inicio

import androidx.compose.runtime.Immutable
import com.plantaoseguro.agenda.Agenda
import com.plantaoseguro.agenda.Ocorrencia
import com.plantaoseguro.modelo.Dados
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.sistema.Requisito
import com.plantaoseguro.ui.comum.DiaEscala
import com.plantaoseguro.ui.comum.Trecho
import com.plantaoseguro.ui.tema.Paleta
import com.plantaoseguro.util.Formato
import java.time.Instant
import java.time.ZoneId

@Immutable
data class ResumoPlantao(
    val plantao: Plantao,
    val quando: String,
    val horario: String,
    val valendo: Boolean,
    val situacao: String?,
    val temFolga: Boolean
)

@Immutable
data class InicioEstado(
    val agora: Long = 0L,
    val ativas: List<Ocorrencia> = emptyList(),
    val proxima: Ocorrencia? = null,
    val alerta: AlertaAtivo? = null,
    val pendente: AlertaAtivo? = null,
    val plantoes: List<ResumoPlantao> = emptyList(),
    val semana: List<DiaEscala> = emptyList(),
    val pendencias: List<Requisito> = emptyList(),
    val nomesApps: List<String> = emptyList(),
    val regrasLigadas: Int = 0,
    val regrasSempre: Int = 0,
    val escutaLigada: Boolean = true,
    val duracaoRapidoMin: Int = 12 * 60
) {
    val emPlantao: Boolean get() = ativas.isNotEmpty()

    /** Os alertas estão de fato prontos para tocar agora. */
    val armado: Boolean get() = (ativas.any { it.plantao.tocarSirene } && regrasLigadas > 0) || regrasSempre > 0
}

/** Monta o estado da tela inicial a partir dos dados e do relógio. Função pura. */
fun montarInicio(
    dados: Dados,
    alerta: AlertaAtivo?,
    pendente: AlertaAtivo?,
    agora: Long,
    pendencias: List<Requisito>,
    nomeDoApp: (String) -> String,
    escutaLigada: Boolean,
    zona: ZoneId = ZoneId.systemDefault()
): InicioEstado {
    val pulos = dados.execucao.pulos
    val ativas = Agenda.ativas(dados.plantoes, agora, zona, pulos)
    val proxima = Agenda.proxima(dados.plantoes, agora, zona, pulos)
    val regrasLigadas = dados.regras.filter { it.ligada && it.apps.isNotEmpty() && !it.silenciada(agora) }

    val visiveis = dados.plantoes
        .filter { !(it.rapido && it.fimMs <= agora) }
        .sortedWith(compareBy<Plantao> { it.tipo }.thenBy { it.inicioMin }.thenBy { it.inicioMs })

    val resumos = visiveis.map { p ->
        val valendo = ativas.any { it.plantao.id == p.id }
        val prox = Agenda.proxima(listOf(p), agora, zona, pulos)
        val temFolga = pulos.any { it.plantaoId == p.id && it.inicioOcorrencia > agora }
        val quando = when (p.tipo) {
            TipoAgenda.SEMANAL -> Formato.dias(p.dias)
            TipoAgenda.UNICO -> if (p.rapido) "Começou ${Formato.quando(p.inicioMs, agora, zona)}"
            else "Uma vez, ${Formato.diaMes(p.inicioMs, zona)}"
        }
        val horario = when (p.tipo) {
            TipoAgenda.SEMANAL -> Formato.horaMin(p.inicioMin) + " – " + Formato.horaMin(p.fimMin) +
                if (Agenda.terminaNoDiaSeguinte(p)) " (+1)" else ""
            TipoAgenda.UNICO -> Formato.hora(p.inicioMs, zona) + " – " + Formato.hora(p.fimMs, zona)
        }
        val situacao = when {
            !p.ligado -> "Desligado"
            valendo -> "Valendo agora"
            prox != null && temFolga -> "Folga marcada. Depois: ${Formato.quando(prox.inicio, agora, zona)}"
            prox != null -> "Próximo: ${Formato.quando(prox.inicio, agora, zona)}"
            p.tipo == TipoAgenda.UNICO && p.fimMs <= agora -> "Já aconteceu"
            else -> null
        }
        ResumoPlantao(p, quando, horario, valendo, situacao, temFolga)
    }

    val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
    val semana = (0 until 7).map { i ->
        val dia = hoje.plusDays(i.toLong())
        val inicioDia = dia.atStartOfDay(zona).toInstant().toEpochMilli()
        val fimDia = dia.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        val duracaoDia = (fimDia - inicioDia).toFloat()
        val trechos = Agenda.doDia(dados.plantoes, dia, zona, pulos).map { o ->
            Trecho(
                de = (maxOf(o.inicio, inicioDia) - inicioDia) / duracaoDia,
                ate = (minOf(o.fim, fimDia) - inicioDia) / duracaoDia,
                cor = Paleta.corEscala(o.plantao.cor)
            )
        }
        DiaEscala(
            rotulo = if (i == 0) "Hoje" else Formato.DIAS_CURTOS[dia.dayOfWeek.value - 1],
            numero = dia.dayOfMonth.toString(),
            hoje = i == 0,
            trechos = trechos,
            agora = if (i == 0) (agora - inicioDia) / duracaoDia else null
        )
    }

    return InicioEstado(
        agora = agora,
        ativas = ativas,
        proxima = proxima,
        alerta = alerta,
        pendente = pendente,
        plantoes = resumos,
        semana = semana,
        pendencias = pendencias,
        nomesApps = regrasLigadas.flatMap { it.apps }.distinct().map(nomeDoApp),
        regrasLigadas = regrasLigadas.count { it.quando == QuandoVale.NO_PLANTAO },
        regrasSempre = regrasLigadas.count { it.quando == QuandoVale.SEMPRE },
        escutaLigada = escutaLigada,
        duracaoRapidoMin = dados.ajustes.duracaoRapidoMin
    )
}
