package com.plantaoseguro.sistema

import android.content.Context
import androidx.compose.runtime.Immutable
import com.plantaoseguro.Grafo
import com.plantaoseguro.agenda.Agenda
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.servico.PlantaoService
import com.plantaoseguro.util.Formato
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

enum class Gravidade { OK, AVISO, ERRO, INFO }

enum class Correcao { PERMISSOES, RELIGAR_ESCUTA, REGRAS, NENHUMA }

@Immutable
data class Verificacao(
    val titulo: String,
    val gravidade: Gravidade,
    val detalhe: String,
    val correcao: Correcao = Correcao.NENHUMA
)

/** Diz, item por item, se o monitoramento está funcionando e o que fazer se não estiver. */
object Diagnostico {

    fun verificar(context: Context, g: Grafo, agora: Long = System.currentTimeMillis()): List<Verificacao> {
        val d = g.repo.dados.value
        val zona = ZoneId.systemDefault()
        val ativas = Agenda.ativas(d.plantoes, agora, zona, d.execucao.pulos)
        val lista = ArrayList<Verificacao>()

        val autorizada = SentinelaService.autorizada(context)
        lista += if (autorizada) {
            Verificacao("Acesso às notificações", Gravidade.OK, "Liberado.")
        } else {
            Verificacao("Acesso às notificações", Gravidade.ERRO, "Sem isso o app não percebe nenhuma mensagem.", Correcao.PERMISSOES)
        }
        if (autorizada) {
            lista += if (SentinelaService.conectada.value) {
                Verificacao("Escuta das notificações", Gravidade.OK, "Ligada agora.")
            } else {
                Verificacao(
                    "Escuta das notificações", Gravidade.ERRO,
                    "O Android desligou a escuta. Toque em Religar; se não voltar, desligue e ligue o acesso às notificações do app.",
                    Correcao.RELIGAR_ESCUTA
                )
            }
        }

        val regrasLigadas = d.regras.filter { it.ligada && it.apps.isNotEmpty() }
        lista += if (regrasLigadas.isEmpty()) {
            Verificacao("Regras de alerta", Gravidade.ERRO, "Nenhuma regra ligada: nada vai tocar.", Correcao.REGRAS)
        } else {
            val silenciadas = regrasLigadas.count { it.silenciada(agora) }
            Verificacao(
                "Regras de alerta",
                if (silenciadas == regrasLigadas.size) Gravidade.AVISO else Gravidade.OK,
                "${regrasLigadas.size} ligada(s)" + if (silenciadas > 0) ", $silenciadas silenciada(s) por enquanto." else "."
            )
        }

        for (r in Permissoes.aplicaveis().filter { it != Requisito.ESCUTA }) {
            val ok = Permissoes.ok(context, r)
            lista += Verificacao(
                r.titulo,
                when {
                    ok -> Gravidade.OK
                    r == Requisito.NAO_PERTURBE -> Gravidade.AVISO
                    else -> Gravidade.ERRO
                },
                if (ok) "Liberado." else r.porque,
                if (ok) Correcao.NENHUMA else Correcao.PERMISSOES
            )
        }

        lista += if (g.notificador.canalAlertaOk()) {
            Verificacao("Notificação do alerta", Gravidade.OK, "Com prioridade alta.")
        } else {
            Verificacao(
                "Notificação do alerta", Gravidade.ERRO,
                "A categoria \"Alerta tocando\" foi rebaixada nas configurações do Android. A tela cheia não vai aparecer.",
                Correcao.PERMISSOES
            )
        }

        if (ativas.isNotEmpty()) {
            val p = ativas.first()
            lista += Verificacao("Plantão agora", Gravidade.OK, "${p.plantao.nome}, até ${Formato.quando(p.fim, agora)}.")
            lista += if (PlantaoService.rodando) {
                Verificacao("Serviço do plantão", Gravidade.OK, "Rodando em primeiro plano.")
            } else {
                Verificacao(
                    "Serviço do plantão", Gravidade.AVISO,
                    "Não está rodando. O Android pode ter impedido; libere a bateria sem restrição.",
                    Correcao.PERMISSOES
                )
            }
        } else {
            val prox = Agenda.proxima(d.plantoes, agora, zona, d.execucao.pulos)
            lista += Verificacao(
                "Plantão agora", Gravidade.INFO,
                if (prox != null) "Nenhum. Próximo: ${prox.plantao.nome}, ${Formato.quando(prox.inicio, agora)}." else "Nenhum plantão programado."
            )
        }

        val ultima = SentinelaService.ultimaRecebida.value
        lista += Verificacao(
            "Última notificação de app vigiado", Gravidade.INFO,
            if (ultima > 0) "${Formato.quando(ultima, agora)} (só o horário fica registrado)." else "Nenhuma desde que o app abriu."
        )
        val ultimoAlerta = g.historico.eventos.value.firstOrNull { it.tipo == TipoEvento.ALERTA }
        lista += Verificacao(
            "Último alerta", Gravidade.INFO,
            ultimoAlerta?.let { "${Formato.quando(it.t, agora)}: ${it.titulo}." } ?: "Nenhum registrado."
        )
        return lista
    }

    /**
     * Teste ponta a ponta da escuta: o app publica uma notificação própria e
     * mede quanto tempo a escuta leva para percebê-la. Devolve o tempo em ms
     * ou nulo se ela não percebeu em 6 segundos.
     */
    suspend fun testarEscuta(g: Grafo): Long? {
        val antes = SentinelaService.ultimoTeste.value
        val inicio = System.currentTimeMillis()
        g.notificador.postarTesteDeEscuta()
        val resultado = withTimeoutOrNull(6_000) {
            while (SentinelaService.ultimoTeste.value == antes) delay(50)
            System.currentTimeMillis() - inicio
        }
        g.notificador.limparTesteDeEscuta()
        return resultado
    }
}
