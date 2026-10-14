package com.plantaoseguro.agenda

import android.content.Context
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.dados.Historico
import com.plantaoseguro.dados.Migracao25
import com.plantaoseguro.dados.Repositorio
import com.plantaoseguro.modelo.AoTerminar
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Pulo
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.nucleo.CentralDeAlertas
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.servico.PlantaoService
import com.plantaoseguro.som.ControleSom
import com.plantaoseguro.util.Formato
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID

/**
 * O "cérebro" dos plantões: compara o que a agenda diz que deveria valer com
 * o que já foi aplicado e faz a diferença — mexe no som, sobe o serviço,
 * agenda o próximo horário. Chamar várias vezes dá sempre o mesmo resultado.
 */
class Coordenador(
    private val context: Context,
    private val repo: Repositorio,
    private val historico: Historico,
    private val som: ControleSom,
    private val central: CentralDeAlertas,
    private val notificador: Notificador,
    private val agendador: Agendador
) {
    private val trava = Mutex()
    private val zona: ZoneId get() = ZoneId.systemDefault()
    private var escutaFalhouAntes = false

    /** Plantões valendo agora. Sem acesso a disco: serve para decisões rápidas. */
    fun ativasAgora(agora: Long = System.currentTimeMillis()): List<Ocorrencia> {
        val d = repo.dados.value
        return Agenda.ativas(d.plantoes, agora, zona, d.execucao.pulos)
    }

    suspend fun sincronizar(motivo: String) = trava.withLock { sincronizarSemTrava(motivo) }

    private suspend fun sincronizarSemTrava(motivo: String) {
        val agora = System.currentTimeMillis()

        // Limpeza: plantões rápidos antigos, folgas que já passaram, silêncios vencidos.
        val dados = repo.atualizar { d ->
            d.copy(
                plantoes = d.plantoes.filterNot { it.rapido && it.fimMs < agora - DIA_MS },
                regras = d.regras.map { if (it.silenciadaAte in 1..agora) it.copy(silenciadaAte = 0L) else it },
                execucao = d.execucao.copy(
                    pulos = d.execucao.pulos.filter { it.inicioOcorrencia > agora - 8 * DIA_MS }
                )
            )
        }

        val ativas = Agenda.ativas(dados.plantoes, agora, zona, dados.execucao.pulos)
        val chaves = ativas.map { it.chave }
        val aplicadas = dados.execucao.ocorrenciasAplicadas
        val novas = ativas.filter { it.chave !in aplicadas }
        val encerradas = aplicadas.filter { it !in chaves }
        var exec = dados.execucao

        if (ativas.isNotEmpty()) {
            if (aplicadas.isEmpty() && exec.somAntes == null) {
                exec = exec.copy(somAntes = som.salvar())
            }
            novas.forEach { aplicarInicio(it) }
            exec = exec.copy(restaurarAoFim = ativas.any { it.plantao.aoTerminar == AoTerminar.RESTAURAR })
            val principal = ativas.first()
            PlantaoService.iniciar(context)
            notificador.atualizarPlantao(principal.plantao.nome, principal.fim, ativas.any { it.plantao.tocarSirene })
            verificarSaude(ativas, registrar = novas.isNotEmpty())
            agendador.agendarVerificacao(agora + VERIFICACAO_MS)
        } else {
            agendador.agendarVerificacao(null)
        }

        if (encerradas.isNotEmpty()) {
            val nomes = encerradas.mapNotNull { chave ->
                dados.plantoes.firstOrNull { chave.startsWith(it.id + "@") }?.nome
            }.distinct()
            historico.registrar(
                TipoEvento.PLANTAO_FIM, Nivel.INFO,
                "Plantão encerrado",
                (if (nomes.isEmpty()) "" else nomes.joinToString(", ") + ". ") +
                    "Motivo: ${motivo.replaceFirstChar { it.uppercase() }}."
            )
            if (ativas.none { it.plantao.tocarSirene }) central.encerrarPorFimDePlantao()
            if (ativas.isEmpty()) {
                val salvo = exec.somAntes
                val restaurou = exec.restaurarAoFim && salvo != null
                if (salvo != null && restaurou) som.restaurar(salvo)
                exec = exec.copy(somAntes = null, volumeReduzidoAte = 0L)
                agendador.agendarVoltaDoVolume(null)
                PlantaoService.parar(context)
                notificador.limparPlantao()
                notificador.limparProblema()
                notificador.aviso(
                    "Plantão encerrado",
                    if (restaurou) "Os alertas foram desligados e o som do celular voltou a ser como antes."
                    else "Os alertas foram desligados. Os apps voltam a tocar o som normal deles."
                )
            }
        }

        exec = exec.copy(ocorrenciasAplicadas = chaves)
        val final = exec
        val atualizados = repo.atualizar { it.copy(execucao = final) }
        agendador.agendarTransicao(
            Agenda.proximaTransicao(atualizados.plantoes, agora, zona, atualizados.execucao.pulos)
        )
    }

    private fun aplicarInicio(o: Ocorrencia) {
        val p = o.plantao
        val silOk = !p.tirarDoSilencioso || som.tirarDoSilencioso()
        val dndOk = !p.desligarNaoPerturbe || som.desligarNaoPerturbe()
        if (p.vigiarVolume) som.subirVolumePara(p.volumeMinimo)
        val falhas = buildList {
            if (!silOk) add("não consegui tirar do silencioso")
            if (!dndOk) add("não consegui desligar o Não Perturbe (falta permissão)")
        }
        historico.registrar(
            TipoEvento.PLANTAO_INICIO,
            if (falhas.isEmpty()) Nivel.OK else Nivel.AVISO,
            "Plantão começou: ${p.nome}",
            buildString {
                append("Vai até ${Formato.quando(o.fim)}.")
                if (p.tocarSirene) append(" Alertas ligados.") else append(" Sem alertas, só o som do celular.")
                if (falhas.isNotEmpty()) append(" Atenção: ").append(falhas.joinToString(" e ")).append('.')
            }
        )
        notificador.aviso(
            "Plantão começou: ${p.nome}",
            if (p.tocarSirene) "Os alertas ficam ligados até ${Formato.quando(o.fim)}."
            else "O som do celular fica garantido até ${Formato.quando(o.fim)}."
        )
    }

    private fun verificarSaude(ativas: List<Ocorrencia>, registrar: Boolean) {
        if (ativas.none { it.plantao.tocarSirene }) {
            notificador.limparProblema()
            return
        }
        val problemas = buildList {
            if (!SentinelaService.autorizada(context)) {
                add("O app está sem acesso às notificações, então não vai perceber as mensagens.")
            }
            if (repo.dados.value.regras.none { it.ligada && it.apps.isNotEmpty() }) {
                add("Nenhuma regra de alerta está ligada.")
            }
            if (!notificador.canalAlertaOk()) {
                add("A notificação do alerta foi rebaixada nas configurações do Android; a tela cheia não vai aparecer.")
            }
        }
        if (problemas.isEmpty()) {
            notificador.limparProblema()
        } else {
            val texto = problemas.joinToString(" ")
            notificador.problema("O alerta pode não tocar", "$texto Toque para ver o diagnóstico.")
            if (registrar) historico.registrar(TipoEvento.PROBLEMA, Nivel.ERRO, "O alerta pode não tocar", texto)
        }
    }

    /**
     * Verificação a cada 30 min durante o plantão: a escuta de notificações
     * continua ligada? Se o sistema a desligou, pede para religar; se não
     * voltar, avisa.
     */
    suspend fun verificarEscuta() {
        val ativas = ativasAgora()
        if (ativas.isEmpty()) {
            agendador.agendarVerificacao(null)
            return
        }
        agendador.agendarVerificacao(System.currentTimeMillis() + VERIFICACAO_MS)
        if (ativas.none { it.plantao.tocarSirene }) return
        if (!SentinelaService.autorizada(context)) {
            verificarSaude(ativas, registrar = false)
            return
        }
        if (SentinelaService.conectada.value) {
            escutaFalhouAntes = false
            return
        }
        SentinelaService.pedirReconexao(context)
        if (escutaFalhouAntes) {
            notificador.problema(
                "A escuta das notificações parou",
                "O Android desligou a parte do app que percebe as mensagens. Abra o Plantão Seguro para religar."
            )
            historico.registrar(TipoEvento.PROBLEMA, Nivel.ERRO, "Escuta desligada pelo sistema", "Pedi para religar duas vezes seguidas sem sucesso.")
        } else {
            historico.registrar(TipoEvento.SISTEMA, Nivel.AVISO, "Escuta religada", "O sistema tinha desligado a escuta das notificações.")
        }
        escutaFalhouAntes = true
    }

    // ---------------------------------------------------------------- plantões

    suspend fun comecarAgora(duracaoMin: Int) {
        val agora = System.currentTimeMillis()
        val p = Plantao(
            id = "rapido-" + UUID.randomUUID().toString().take(8),
            nome = "Plantão agora",
            tipo = TipoAgenda.UNICO,
            inicioMs = agora,
            fimMs = agora + duracaoMin * 60_000L,
            rapido = true,
            criadoEm = agora
        )
        repo.atualizar { it.copy(plantoes = it.plantoes + p) }
        sincronizar("plantão iniciado na hora")
    }

    /** Encerra o que estiver valendo agora. Plantões semanais voltam na próxima vez normalmente. */
    suspend fun encerrarAgora() = trava.withLock {
        val agora = System.currentTimeMillis()
        val d = repo.dados.value
        val ativas = Agenda.ativas(d.plantoes, agora, zona, d.execucao.pulos)
        if (ativas.isEmpty()) return@withLock
        repo.atualizar { dd ->
            val pulos = dd.execucao.pulos + ativas
                .filter { it.plantao.tipo == TipoAgenda.SEMANAL }
                .map { Pulo(it.plantao.id, it.inicio) }
            val idsUnicos = ativas.filter { it.plantao.tipo == TipoAgenda.UNICO }.map { it.plantao.id }.toSet()
            dd.copy(
                plantoes = dd.plantoes.map { if (it.id in idsUnicos) it.copy(fimMs = agora) else it },
                execucao = dd.execucao.copy(pulos = pulos)
            )
        }
        sincronizarSemTrava("encerrado por você")
    }

    /** Folga: pula a próxima ocorrência de um plantão semanal. */
    suspend fun folgaNaProxima(plantaoId: String): Ocorrencia? {
        val d = repo.dados.value
        val p = d.plantoes.firstOrNull { it.id == plantaoId } ?: return null
        val prox = Agenda.proxima(listOf(p), System.currentTimeMillis(), zona, d.execucao.pulos) ?: return null
        repo.atualizar { dd -> dd.copy(execucao = dd.execucao.copy(pulos = dd.execucao.pulos + Pulo(p.id, prox.inicio))) }
        historico.registrar(TipoEvento.SISTEMA, Nivel.INFO, "Folga marcada", "${p.nome} não vai valer ${Formato.quando(prox.inicio)}.")
        sincronizar("folga marcada")
        return prox
    }

    suspend fun desfazerFolgas(plantaoId: String) {
        repo.atualizar { dd -> dd.copy(execucao = dd.execucao.copy(pulos = dd.execucao.pulos.filterNot { it.plantaoId == plantaoId && it.inicioOcorrencia > System.currentTimeMillis() })) }
        sincronizar("folga desfeita")
    }

    suspend fun salvarPlantao(p: Plantao) {
        repo.atualizar { d ->
            val existe = d.plantoes.any { it.id == p.id }
            d.copy(plantoes = if (existe) d.plantoes.map { if (it.id == p.id) p else it } else d.plantoes + p)
        }
        sincronizar("plantão salvo")
    }

    suspend fun excluirPlantao(id: String) {
        repo.atualizar { d ->
            d.copy(
                plantoes = d.plantoes.filterNot { it.id == id },
                regras = d.regras.map { r -> if (id in r.plantoes) r.copy(plantoes = r.plantoes - id) else r }
            )
        }
        sincronizar("plantão excluído")
    }

    suspend fun ligarDesligarPlantao(id: String, ligado: Boolean) {
        repo.atualizar { d -> d.copy(plantoes = d.plantoes.map { if (it.id == id) it.copy(ligado = ligado) else it }) }
        sincronizar(if (ligado) "plantão ligado" else "plantão desligado")
    }

    // ---------------------------------------------------------------- regras

    suspend fun salvarRegra(r: Regra) {
        repo.atualizar { d ->
            val existe = d.regras.any { it.id == r.id }
            d.copy(regras = if (existe) d.regras.map { if (it.id == r.id) r else it } else d.regras + r)
        }
        sincronizar("regra salva")
    }

    suspend fun excluirRegra(id: String) {
        repo.atualizar { d -> d.copy(regras = d.regras.filterNot { it.id == id }) }
        sincronizar("regra excluída")
    }

    suspend fun ligarDesligarRegra(id: String, ligada: Boolean) {
        repo.atualizar { d -> d.copy(regras = d.regras.map { if (it.id == id) it.copy(ligada = ligada) else it }) }
    }

    /** Silencia uma regra por um tempo (0 = tira o silêncio). */
    suspend fun silenciarRegra(id: String, minutos: Int) {
        val ate = if (minutos <= 0) 0L else System.currentTimeMillis() + minutos * 60_000L
        val d = repo.atualizar { d -> d.copy(regras = d.regras.map { if (it.id == id) it.copy(silenciadaAte = ate) else it }) }
        val nome = d.regras.firstOrNull { it.id == id }?.nome ?: "Regra"
        historico.registrar(
            TipoEvento.SISTEMA, Nivel.INFO,
            if (ate == 0L) "Regra reativada" else "Regra silenciada",
            if (ate == 0L) "$nome voltou a tocar." else "$nome não toca até ${Formato.quando(ate)}."
        )
    }

    // ---------------------------------------------------------------- testes e alertas

    /** Toca o alerta de teste com o toque de uma regra (ou da primeira ligada). */
    fun testar(regraId: String?): Boolean {
        val regras = repo.dados.value.regras
        val r = regras.firstOrNull { it.id == regraId } ?: regras.firstOrNull { it.ligada } ?: Regra.padrao()
        return central.testar(r.toque, r.nome)
    }

    fun testarComTelaBloqueada(segundos: Int) {
        agendador.agendarTeste(System.currentTimeMillis() + segundos * 1000L)
    }

    /** Alarme de insistência: a mensagem continua sem ser aberta? */
    fun insistir(chave: String, pacote: String?) {
        if (chave.isEmpty() || pacote == null) return
        central.insistir(chave, pacote, SentinelaService.notificacaoAtiva(chave))
    }

    // ---------------------------------------------------------------- vigia de volume

    /** Mantém o volume baixo por [minutos]; nulo = até o fim do plantão que vigia o volume. */
    suspend fun manterVolumeBaixo(minutos: Int?) {
        val agora = System.currentTimeMillis()
        val ate = if (minutos != null) {
            agora + minutos * 60_000L
        } else {
            ativasAgora(agora).filter { it.plantao.vigiarVolume }.maxOfOrNull { it.fim } ?: (agora + 60 * 60_000L)
        }
        repo.atualizar { d -> d.copy(execucao = d.execucao.copy(volumeReduzidoAte = ate)) }
        agendador.agendarVoltaDoVolume(ate)
        notificador.limparPerguntaVolume()
        historico.registrar(TipoEvento.SISTEMA, Nivel.INFO, "Volume baixo mantido", "Até ${Formato.hora(ate)}. Depois volta ao mínimo do plantão.")
    }

    suspend fun voltarVolume() {
        repo.atualizar { d -> d.copy(execucao = d.execucao.copy(volumeReduzidoAte = 0L)) }
        agendador.agendarVoltaDoVolume(null)
        notificador.limparPerguntaVolume()
        val minimo = ativasAgora().filter { it.plantao.vigiarVolume }.maxOfOrNull { it.plantao.volumeMinimo } ?: return
        som.subirVolumePara(minimo)
    }

    // ---------------------------------------------------------------- versão anterior

    /** Uma vez só: traz os plantões da 2.5.0 se a v3 foi instalada por cima dela. */
    suspend fun importarVersaoAnterior() {
        if (repo.dados.value.execucao.migracao25Feita) return
        val r = Migracao25.ler(context)
        repo.atualizar { d ->
            if (r == null) {
                d.copy(execucao = d.execucao.copy(migracao25Feita = true))
            } else {
                val extras = if (r.expediente) listOf(Migracao25.expediente(System.currentTimeMillis())) else emptyList()
                d.copy(
                    plantoes = d.plantoes + r.plantoes + extras,
                    execucao = d.execucao.copy(migracao25Feita = true)
                )
            }
        }
        if (r != null) {
            historico.registrar(
                TipoEvento.SISTEMA, Nivel.OK, "Plantões da versão anterior importados",
                "${r.plantoes.size} plantão(ões)" + if (r.expediente) " e o Modo Trabalho como \"Expediente\"." else "."
            )
        }
    }

    private companion object {
        const val DIA_MS = 24L * 60 * 60 * 1000
        const val VERIFICACAO_MS = 30L * 60 * 1000
    }
}
