package com.plantaoseguro.nucleo

import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.modelo.Toque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Um alerta em andamento (ou aguardando confirmação). Nada aqui vai para o disco. */
data class AlertaAtivo(
    val id: Long,
    val regraId: String,
    val regraNome: String,
    val toque: Toque,
    val pacote: String?,
    val appNome: String?,
    /** Notificações de origem que fazem parte deste alerta, com o horário de cada uma. */
    val chaves: Map<String, Long>,
    val quantidade: Int,
    val inicio: Long,
    val chamada: Boolean = false,
    /** Nome da conversa: só preenchido quando a regra usa filtro de contato. */
    val conversa: String? = null,
    val plantaoNome: String? = null,
    val teste: Boolean = false,
    val insistencias: Int = 0,
    /** Alerta de regra que só vale no plantão (para quando o plantão acaba). */
    val soNoPlantao: Boolean = true
)

/** Por que a notificação de origem sumiu, traduzido dos motivos do Android. */
enum class MotivoRemocao(val texto: String) {
    TOCOU("Você tocou na notificação da mensagem"),
    DISPENSOU("Você dispensou a notificação da mensagem"),
    APP_REMOVEU("A notificação foi fechada pelo app (mensagem aberta ou apagada)"),
    CHAMADA_ENCERRADA("A chamada foi atendida ou encerrada"),
    OUTRO("A notificação saiu da tela")
}

/** Tudo que a central faz no mundo real. Em teste, é substituído por um falso. */
interface Efeitos {
    fun tocar(toque: Toque, alerta: AlertaAtivo): Boolean
    fun renovar(toque: Toque)
    fun parar()
    fun mostrarAlerta(alerta: AlertaAtivo)
    fun limparAlerta()
    fun mostrarNaoConfirmado(alerta: AlertaAtivo)
    fun limparNaoConfirmado()
    fun problema(titulo: String, texto: String)
    fun registrar(tipo: TipoEvento, nivel: Nivel, titulo: String, detalhe: String?)
    fun agendarInsistencia(chave: String, pacote: String, quando: Long)
    fun cancelarInsistencia(chave: String)
}

/**
 * Máquina de estados dos alertas: ocioso → tocando → confirmado ou não
 * confirmado. Garante um alarme por vez (novas mensagens somam no alerta que
 * já está tocando) e que uma mensagem confirmada não volta a tocar.
 *
 * Deve ser usada sempre na mesma thread (a principal).
 */
class CentralDeAlertas(
    private val efeitos: Efeitos,
    val memoria: Memoria = Memoria(),
    private val relogio: () -> Long = System::currentTimeMillis
) {
    private val _tocando = MutableStateFlow<AlertaAtivo?>(null)
    val tocando: StateFlow<AlertaAtivo?> = _tocando.asStateFlow()

    private val _pendente = MutableStateFlow<AlertaAtivo?>(null)
    /** Alerta que tocou até o fim sem ninguém confirmar. */
    val pendente: StateFlow<AlertaAtivo?> = _pendente.asStateFlow()

    private var proximoId = 1L

    /** Uma notificação virou alerta. Devolve true se há som/vibração acontecendo. */
    fun disparar(
        d: Decisao.Alertar,
        n: Recebida,
        appNome: String,
        conversa: String?
    ): Boolean {
        val agora = relogio()
        val atual = _tocando.value
        if (atual != null && !atual.teste) {
            // Um alarme por vez: a mensagem nova soma no alerta que já está tocando.
            _tocando.value = atual.copy(chaves = atual.chaves + (n.chave to n.quando), quantidade = atual.quantidade + 1)
            efeitos.renovar(atual.toque)
            efeitos.mostrarAlerta(_tocando.value!!)
            agendarInsistenciaSePreciso(d, n, agora)
            return true
        }
        if (atual != null) {
            efeitos.parar()
            _tocando.value = null
        }
        // Se havia um alerta esperando confirmação do mesmo tipo, ele é substituído pelo novo.
        val pendente = _pendente.value
        if (pendente != null) {
            _pendente.value = null
            efeitos.limparNaoConfirmado()
        }
        val alerta = AlertaAtivo(
            id = proximoId++,
            regraId = d.regra.id,
            regraNome = d.regra.nome,
            toque = d.regra.toque,
            pacote = n.pacote,
            appNome = appNome,
            chaves = mapOf(n.chave to n.quando) + (pendente?.chaves ?: emptyMap()),
            quantidade = 1 + (pendente?.quantidade ?: 0),
            inicio = agora,
            chamada = d.chamada,
            conversa = conversa,
            plantaoNome = d.plantao?.plantao?.nome,
            soNoPlantao = d.regra.quando == QuandoVale.NO_PLANTAO
        )
        val ok = efeitos.tocar(alerta.toque, alerta)
        if (ok) {
            _tocando.value = alerta
            efeitos.mostrarAlerta(alerta)
            efeitos.registrar(
                TipoEvento.ALERTA, Nivel.OK,
                if (d.chamada) "Alerta: chamada de trabalho" else "Alerta: ${d.regra.nome}",
                buildString {
                    append(appNome)
                    alerta.plantaoNome?.let { append(", durante $it") }
                    append('.')
                }
            )
        } else {
            efeitos.registrar(
                TipoEvento.ALERTA, Nivel.ERRO, "O alerta não conseguiu tocar",
                "Regra ${d.regra.nome}, $appNome. Verifique o som escolhido e faça um teste."
            )
            efeitos.problema("O alerta não conseguiu tocar", "Chegou uma notificação no $appNome. Abra o Plantão Seguro e faça um teste.")
        }
        agendarInsistenciaSePreciso(d, n, agora)
        return ok
    }

    private fun agendarInsistenciaSePreciso(d: Decisao.Alertar, n: Recebida, agora: Long) {
        val min = d.regra.toque.insistirMin
        if (min > 0 && !d.chamada) efeitos.agendarInsistencia(n.chave, n.pacote, agora + min * 60_000L)
    }

    /**
     * A notificação de origem foi removida. Decide se isso confirma o alerta.
     * [motivo] nulo = um motivo que não indica que você viu (ex.: expirou);
     * nesse caso o alerta continua.
     */
    fun notificacaoRemovida(chave: String, motivo: MotivoRemocao?, pararAoTocarNaMensagem: Boolean) {
        memoria.esquecer(chave)
        if (motivo == null) return
        efeitos.cancelarInsistencia(chave)
        val tocando = _tocando.value
        if (tocando != null && chave in tocando.chaves) {
            if (tocando.chamada || pararAoTocarNaMensagem) confirmar(motivo.texto)
            return
        }
        val pendente = _pendente.value
        if (pendente != null && chave in pendente.chaves && (pendente.chamada || pararAoTocarNaMensagem)) {
            confirmar(motivo.texto)
        }
    }

    /** Você confirmou que viu: para o som e marca as mensagens como vistas. */
    fun confirmar(como: String): Boolean {
        val tocando = _tocando.value
        val pendente = _pendente.value
        val alerta = tocando ?: pendente ?: return false
        if (tocando != null) {
            efeitos.parar()
            efeitos.limparAlerta()
            _tocando.value = null
        }
        if (pendente != null) {
            efeitos.limparNaoConfirmado()
            _pendente.value = null
        }
        alerta.chaves.forEach { (chave, quando) ->
            memoria.confirmar(chave, quando)
            efeitos.cancelarInsistencia(chave)
        }
        if (alerta.teste) {
            efeitos.registrar(TipoEvento.TESTE, Nivel.OK, "Teste parado", "$como.")
        } else {
            val seg = ((relogio() - alerta.inicio) / 1000).coerceAtLeast(0)
            efeitos.registrar(
                TipoEvento.ALERTA_CONFIRMADO, Nivel.INFO, "Alerta confirmado",
                "$como, ${if (tocando != null) "depois de $seg s tocando" else "depois que o alerta já tinha parado"}."
            )
        }
        return true
    }

    /** O som parou sozinho (tempo máximo ou repetições). O alerta fica aguardando confirmação. */
    fun tempoEsgotado() {
        val alerta = _tocando.value ?: return
        _tocando.value = null
        efeitos.limparAlerta()
        if (alerta.teste) {
            efeitos.registrar(TipoEvento.TESTE, Nivel.OK, "Teste concluído", "O som tocou até o fim.")
            return
        }
        _pendente.value = alerta
        efeitos.mostrarNaoConfirmado(alerta)
        efeitos.registrar(
            TipoEvento.ALERTA_NAO_CONFIRMADO, Nivel.AVISO, "Alerta não confirmado",
            "${alerta.appNome ?: "Notificação"}: tocou até o fim e ninguém parou."
        )
    }

    /**
     * Hora de insistir: se o alerta continua sem confirmação e a notificação
     * de origem continua lá, toca de novo.
     */
    fun insistir(chave: String, pacote: String, notificacaoAtiva: Boolean) {
        val pendente = _pendente.value
        val tocando = _tocando.value
        if (tocando != null && chave in tocando.chaves) {
            if (tocando.toque.insistirMin > 0) efeitos.agendarInsistencia(chave, pacote, relogio() + tocando.toque.insistirMin * 60_000L)
            return
        }
        if (pendente == null || chave !in pendente.chaves) return
        if (!notificacaoAtiva) {
            // A mensagem já foi aberta ou apagada fora da nossa vista.
            _pendente.value = null
            efeitos.limparNaoConfirmado()
            return
        }
        if (tocando != null) {
            efeitos.agendarInsistencia(chave, pacote, relogio() + pendente.toque.insistirMin * 60_000L)
            return
        }
        val novo = pendente.copy(inicio = relogio(), insistencias = pendente.insistencias + 1)
        if (efeitos.tocar(novo.toque, novo)) {
            _pendente.value = null
            efeitos.limparNaoConfirmado()
            _tocando.value = novo
            efeitos.mostrarAlerta(novo)
            efeitos.registrar(
                TipoEvento.ALERTA, Nivel.AVISO, "Tocando de novo",
                "${novo.appNome ?: "Notificação"} continua sem confirmação (${novo.insistencias}ª vez)."
            )
        }
        if (novo.toque.insistirMin > 0) efeitos.agendarInsistencia(chave, pacote, relogio() + novo.toque.insistirMin * 60_000L)
    }

    /** Toca um alerta de teste com o toque de uma regra. */
    fun testar(toque: Toque, nomeRegra: String): Boolean {
        _tocando.value?.let { if (!it.teste) return false }
        if (_tocando.value != null) {
            efeitos.parar()
            _tocando.value = null
        }
        val alerta = AlertaAtivo(
            id = proximoId++, regraId = "teste", regraNome = nomeRegra, toque = toque,
            pacote = null, appNome = null, chaves = emptyMap(), quantidade = 1,
            inicio = relogio(), teste = true
        )
        val ok = efeitos.tocar(toque, alerta)
        if (ok) {
            _tocando.value = alerta
            efeitos.mostrarAlerta(alerta)
        }
        efeitos.registrar(
            TipoEvento.TESTE, if (ok) Nivel.OK else Nivel.ERRO,
            if (ok) "Teste do alerta: tocou" else "Teste do alerta: não tocou",
            "Regra $nomeRegra."
        )
        return ok
    }

    /** O plantão acabou enquanto um alerta de plantão tocava ou aguardava. */
    fun encerrarPorFimDePlantao() {
        val tocando = _tocando.value
        if (tocando != null && !tocando.teste && tocando.soNoPlantao) {
            efeitos.parar()
            efeitos.limparAlerta()
            _tocando.value = null
            efeitos.registrar(TipoEvento.ALERTA, Nivel.INFO, "Alerta parado", "O plantão terminou enquanto o alerta tocava.")
        }
        if (_pendente.value?.soNoPlantao == true) {
            _pendente.value = null
            efeitos.limparNaoConfirmado()
        }
    }
}
