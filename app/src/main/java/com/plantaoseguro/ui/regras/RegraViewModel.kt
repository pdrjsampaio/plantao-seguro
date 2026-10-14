package com.plantaoseguro.ui.regras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantaoseguro.Grafo
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

/** O que a biblioteca de sons devolve para o editor de regra. */
@Serializable
data class EscolhaSom(val som: SomRef, val volume: Int)

class RegraViewModel(private val grafo: Grafo, idInicial: String?) : ViewModel() {

    private val existente: Regra? = grafo.repo.dados.value.regras.firstOrNull { it.id == idInicial }
    val nova: Boolean = existente == null

    private val _regra = MutableStateFlow(
        existente ?: Regra(id = UUID.randomUUID().toString(), nome = "", criadaEm = System.currentTimeMillis())
    )
    val regra: StateFlow<Regra> = _regra.asStateFlow()

    private val _erro = MutableStateFlow<String?>(null)
    val erro: StateFlow<String?> = _erro.asStateFlow()

    /** Nomes dos plantões, para escolher em quais a regra vale. */
    val plantoes: List<Pair<String, String>> =
        grafo.repo.dados.value.plantoes.filterNot { it.rapido }.map { it.id to it.nome }

    fun mudar(f: (Regra) -> Regra) {
        _regra.update(f)
        _erro.value = null
    }

    fun mudarToque(f: (Toque) -> Toque) = mudar { it.copy(toque = f(it.toque)) }

    fun aplicarSom(e: EscolhaSom) = mudarToque { it.copy(som = e.som, volume = e.volume, tocarSom = true) }

    fun aplicarApps(apps: Set<String>) = mudar { it.copy(apps = apps) }

    fun previaVibracao(v: Vibracao) = grafo.sirene.previaVibracao(v)

    fun testar() {
        val r = _regra.value
        grafo.central.testar(r.toque, r.nome.ifBlank { "Nova regra" })
    }

    fun salvar(aoTerminar: () -> Unit) {
        val r = _regra.value
        val problema = when {
            r.apps.isEmpty() -> "Escolha pelo menos um app."
            !r.toque.tocarSom && r.toque.vibracao == Vibracao.NENHUMA -> "Escolha um som ou uma vibração; do jeito que está, o alerta não faria nada."
            else -> null
        }
        if (problema != null) {
            _erro.value = problema
            return
        }
        viewModelScope.launch {
            grafo.coordenador.salvarRegra(
                r.copy(
                    nome = r.nome.trim().ifEmpty { "Regra" },
                    contatos = r.contatos.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    palavras = r.palavras.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    ignorar = r.ignorar.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                )
            )
            aoTerminar()
        }
    }

    fun excluir(aoTerminar: () -> Unit) {
        val id = _regra.value.id
        viewModelScope.launch {
            grafo.coordenador.excluirRegra(id)
            aoTerminar()
        }
    }

    fun silenciar(minutos: Int) {
        val id = _regra.value.id
        val ate = if (minutos <= 0) 0L else System.currentTimeMillis() + minutos * 60_000L
        mudar { it.copy(silenciadaAte = ate) }
        if (!nova) viewModelScope.launch { grafo.coordenador.silenciarRegra(id, minutos) }
    }
}
