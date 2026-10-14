package com.plantaoseguro.ui.plantao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantaoseguro.Grafo
import com.plantaoseguro.agenda.Agenda
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.TipoAgenda
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class EditorViewModel(private val grafo: Grafo, idInicial: String?) : ViewModel() {

    private val existente: Plantao? = grafo.repo.dados.value.plantoes.firstOrNull { it.id == idInicial }
    val novo: Boolean = existente == null

    private val _plantao = MutableStateFlow(existente ?: plantaoNovo())
    val plantao: StateFlow<Plantao> = _plantao.asStateFlow()

    private val _erro = MutableStateFlow<String?>(null)
    val erro: StateFlow<String?> = _erro.asStateFlow()

    fun mudar(f: (Plantao) -> Plantao) {
        _plantao.update(f)
        _erro.value = null
    }

    fun salvar(aoTerminar: () -> Unit) {
        val p = _plantao.value
        val problema = validar(p, System.currentTimeMillis())
        if (problema != null) {
            _erro.value = problema
            return
        }
        viewModelScope.launch {
            grafo.coordenador.salvarPlantao(p.copy(nome = p.nome.trim().ifEmpty { "Plantão" }))
            aoTerminar()
        }
    }

    fun excluir(aoTerminar: () -> Unit) {
        val id = _plantao.value.id
        viewModelScope.launch {
            grafo.coordenador.excluirPlantao(id)
            aoTerminar()
        }
    }

    private fun plantaoNovo(): Plantao {
        val usadas = grafo.repo.dados.value.plantoes.map { it.cor }.toSet()
        val cor = (0 until 6).firstOrNull { it !in usadas } ?: 0
        val zona = ZoneId.systemDefault()
        val proximaHora = LocalDateTime.now(zona).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val inicio = proximaHora.atZone(zona).toInstant().toEpochMilli()
        return Plantao(
            id = UUID.randomUUID().toString(),
            nome = "",
            cor = cor,
            inicioMs = inicio,
            fimMs = inicio + 12 * 3_600_000L,
            criadoEm = System.currentTimeMillis()
        )
    }

    companion object {
        fun validar(p: Plantao, agora: Long): String? = when (p.tipo) {
            TipoAgenda.SEMANAL -> if (p.dias.isEmpty()) "Escolha pelo menos um dia da semana." else null
            TipoAgenda.UNICO -> when {
                p.fimMs <= p.inicioMs -> "O fim precisa ser depois do começo."
                Agenda.duracaoMin(p) > 7 * 24 * 60 -> "Um plantão avulso pode durar no máximo 7 dias."
                p.fimMs <= agora -> "Esse horário já passou."
                else -> null
            }
        }
    }
}
