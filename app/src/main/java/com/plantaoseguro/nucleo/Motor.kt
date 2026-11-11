package com.plantaoseguro.nucleo

import com.plantaoseguro.agenda.Ocorrencia
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra

/**
 * Uma notificação recebida, já reduzida ao que importa para decidir.
 * O adaptador Android preenche [conteudo] somente se alguma regra do app
 * usa filtro de contato ou palavra.
 */
data class Recebida(
    val chave: String,
    val pacote: String,
    /** Horário que o app de origem pôs na notificação (geralmente o da mensagem). */
    val quando: Long,
    val postadaEm: Long,
    val emAndamento: Boolean = false,
    val resumoDeGrupo: Boolean = false,
    val servicoPrimeiroPlano: Boolean = false,
    val soAlertarUmaVez: Boolean = false,
    val categoria: String? = null,
    val telaCheia: Boolean = false,
    val conteudo: Conteudo? = null
)

enum class MotivoIgnorar {
    NENHUMA_REGRA,
    REGRA_SILENCIADA,
    NAO_E_MENSAGEM,
    ATUALIZACAO,
    JA_CONFIRMADA,
    ANTIGA,
    FORA_DO_PLANTAO,
    CHAMADA_DESLIGADA,
    FILTRO,
    EXCECAO
}

sealed interface Decisao {
    data class Ignorar(val motivo: MotivoIgnorar) : Decisao
    data class Alertar(val regra: Regra, val chamada: Boolean, val plantao: Ocorrencia?) : Decisao
}

/**
 * Memória curta do que já passou pela escuta: serve para reconhecer
 * atualizações da mesma notificação e mensagens já confirmadas.
 */
class Memoria(private val capacidade: Int = 400) {
    private val vistas = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > capacidade
    }
    private val confirmadas = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > capacidade
    }

    fun quandoVisto(chave: String): Long? = vistas[chave]
    fun registrar(chave: String, quando: Long) { vistas[chave] = quando }
    fun esquecer(chave: String) { vistas.remove(chave) }
    fun confirmar(chave: String, quando: Long) { confirmadas[chave] = quando }
    fun confirmada(chave: String, quando: Long): Boolean = confirmadas[chave] == quando
}

/** Decide, sem tocar em nada do Android, se uma notificação deve virar alerta. */
object Motor {

    /** Categorias que nunca são mensagem de alguém. */
    private val categoriasIgnoradas = setOf(
        "progress", "service", "transport", "sys", "status", "navigation",
        "location_sharing", "stopwatch", "workout", "recommendation", "promo"
    )

    /** Mensagens com horário mais antigo que isto nunca disparam. */
    const val LIMITE_ANTIGA_MS = 12L * 60 * 60 * 1000

    /** Tolerância de relógio para "mensagem de antes do plantão". */
    const val TOLERANCIA_INICIO_MS = 60_000L

    /** Regras ligadas e não silenciadas para o app, as críticas primeiro. */
    fun candidatas(regras: List<Regra>, pacote: String, agora: Long): List<Regra> =
        regras.filter { it.ligada && pacote in it.apps && !it.silenciada(agora) }
            .sortedByDescending { it.critica }

    /** Algum app está sendo vigiado por alguma regra ligada? */
    fun monitora(regras: List<Regra>, pacote: String): Boolean =
        regras.any { it.ligada && pacote in it.apps }

    /** O adaptador só lê o texto da notificação quando isto é verdade. */
    fun precisaConteudo(regras: List<Regra>, pacote: String, agora: Long): Boolean =
        candidatas(regras, pacote, agora).any { it.usaConteudo }

    fun decidir(
        n: Recebida,
        regras: List<Regra>,
        ativas: List<Ocorrencia>,
        ajustes: Ajustes,
        memoria: Memoria,
        agora: Long
    ): Decisao {
        val doApp = regras.filter { it.ligada && n.pacote in it.apps }
        if (doApp.isEmpty()) return Decisao.Ignorar(MotivoIgnorar.NENHUMA_REGRA)

        val anterior = memoria.quandoVisto(n.chave)
        memoria.registrar(n.chave, n.quando)
        val jaVista = anterior != null
        val mesmoHorario = anterior != null && anterior == n.quando

        val candidatas = doApp.filter { !it.silenciada(agora) }.sortedByDescending { it.critica }
        if (candidatas.isEmpty()) return Decisao.Ignorar(MotivoIgnorar.REGRA_SILENCIADA)

        // Que tipo de notificação é esta?
        val chamada = n.categoria == "call" && n.telaCheia
        if (n.categoria == "call") {
            if (n.telaCheia) {
                if (jaVista) return Decisao.Ignorar(MotivoIgnorar.ATUALIZACAO)
            } else if (n.emAndamento || n.servicoPrimeiroPlano || jaVista) {
                // Chamada em andamento, ou atualização de chamada perdida.
                return Decisao.Ignorar(MotivoIgnorar.NAO_E_MENSAGEM)
            }
        } else {
            if (n.emAndamento || n.servicoPrimeiroPlano || n.resumoDeGrupo) return Decisao.Ignorar(MotivoIgnorar.NAO_E_MENSAGEM)
            if (n.categoria in categoriasIgnoradas) return Decisao.Ignorar(MotivoIgnorar.NAO_E_MENSAGEM)
            if (jaVista && (n.soAlertarUmaVez || mesmoHorario)) return Decisao.Ignorar(MotivoIgnorar.ATUALIZACAO)
        }
        if (memoria.confirmada(n.chave, n.quando)) return Decisao.Ignorar(MotivoIgnorar.JA_CONFIRMADA)

        val horario = if (n.quando > 0) n.quando else n.postadaEm
        if (!chamada && horario < agora - LIMITE_ANTIGA_MS) return Decisao.Ignorar(MotivoIgnorar.ANTIGA)

        var motivo = MotivoIgnorar.FORA_DO_PLANTAO
        for (r in candidatas) {
            if (chamada && !r.chamadas) {
                motivo = MotivoIgnorar.CHAMADA_DESLIGADA
                continue
            }
            val emPlantao = ativas.firstOrNull { it.plantao.tocarSirene && (r.plantoes.isEmpty() || it.plantao.id in r.plantoes) }
            if (r.quando == QuandoVale.NO_PLANTAO) {
                if (emPlantao == null) {
                    motivo = MotivoIgnorar.FORA_DO_PLANTAO
                    continue
                }
                if (ajustes.ignorarAnteriores && !chamada && horario < emPlantao.inicio - TOLERANCIA_INICIO_MS) {
                    motivo = MotivoIgnorar.ANTIGA
                    continue
                }
            }
            if (r.usaConteudo) {
                val c = n.conteudo
                if (c == null) {
                    motivo = MotivoIgnorar.FILTRO
                    continue
                }
                if (r.ignorar.isNotEmpty() && Texto.casaPalavra(c, r.ignorar)) {
                    motivo = MotivoIgnorar.EXCECAO
                    continue
                }
                if (r.contatos.isNotEmpty() && !Texto.casaContato(c, r.contatos)) {
                    motivo = MotivoIgnorar.FILTRO
                    continue
                }
                if (r.palavras.isNotEmpty() && !Texto.casaPalavra(c, r.palavras)) {
                    motivo = MotivoIgnorar.FILTRO
                    continue
                }
            }
            return Decisao.Alertar(r, chamada, emPlantao)
        }
        return Decisao.Ignorar(motivo)
    }
}
