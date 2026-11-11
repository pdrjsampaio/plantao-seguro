package com.plantaoseguro.ui.inicio

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantaoseguro.Grafo
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.sistema.Apps
import com.plantaoseguro.sistema.Permissoes
import com.plantaoseguro.sistema.Requisito
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InicioViewModel(private val grafo: Grafo, private val app: Application) : ViewModel() {

    private val relogio = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15_000)
        }
    }
    private val pendencias = MutableStateFlow<List<Requisito>>(emptyList())
    private val nomes = HashMap<String, String>()

    private val alertas = combine(grafo.central.tocando, grafo.central.pendente) { t, p -> t to p }

    val estado: StateFlow<InicioEstado> = combine(
        grafo.repo.dados, alertas, relogio, pendencias, SentinelaService.conectada
    ) { dados, (tocando, pendente), agora, pend, conectada ->
        montarInicio(
            dados, tocando, pendente, agora, pend,
            nomeDoApp = { pkg -> nomes.getOrPut(pkg) { Apps.nome(app, pkg) } },
            escutaLigada = conectada || !SentinelaService.autorizada(app)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioEstado())

    fun atualizarPendencias() {
        pendencias.value = Permissoes.pendentes(app)
        SentinelaService.pedirReconexao(app)
    }

    fun comecarAgora(minutos: Int) {
        viewModelScope.launch { grafo.coordenador.comecarAgora(minutos) }
    }

    fun encerrar() {
        viewModelScope.launch { grafo.coordenador.encerrarAgora() }
    }

    fun testar() {
        grafo.coordenador.testar(null)
    }

    fun confirmar() {
        grafo.central.confirmar("Você tocou em Parar no app")
    }

    fun ligarDesligar(id: String, ligado: Boolean) {
        viewModelScope.launch { grafo.coordenador.ligarDesligarPlantao(id, ligado) }
    }

    fun folga(id: String) {
        viewModelScope.launch { grafo.coordenador.folgaNaProxima(id) }
    }

    fun desfazerFolga(id: String) {
        viewModelScope.launch { grafo.coordenador.desfazerFolgas(id) }
    }
}
