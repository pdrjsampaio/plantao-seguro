package com.plantaoseguro

import android.app.Application
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.sentinela.SentinelaService
import kotlinx.coroutines.launch

class PlantaoApp : Application() {

    lateinit var grafo: Grafo
        private set

    override fun onCreate() {
        super.onCreate()
        grafo = Grafo(this)
        grafo.notificador.criarCanais()
        grafo.repo.falhaAoLer?.let { motivo ->
            grafo.historico.registrar(TipoEvento.PROBLEMA, Nivel.ERRO, "Configurações não puderam ser lidas", motivo)
            grafo.notificador.problema("Configurações não puderam ser lidas", motivo)
        }
        SentinelaService.pedirReconexao(this)
        // Toda vez que o processo sobe (app aberto, alarme, notificação, celular
        // ligado...) o estado é recalculado a partir da agenda.
        grafo.escopo.launch {
            grafo.coordenador.importarVersaoAnterior()
            grafo.coordenador.sincronizar("app iniciado")
        }
    }
}
