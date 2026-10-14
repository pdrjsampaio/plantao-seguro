package com.plantaoseguro.servico

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.util.Formato
import kotlinx.coroutines.launch

/** Botão "Plantão" nas configurações rápidas: um toque começa ou encerra. */
class PlantaoTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        atualizar()
    }

    override fun onClick() {
        super.onClick()
        val grafo = (application as PlantaoApp).grafo
        grafo.escopo.launch {
            if (grafo.coordenador.ativasAgora().isNotEmpty()) {
                grafo.coordenador.encerrarAgora()
            } else {
                grafo.coordenador.comecarAgora(grafo.repo.dados.value.ajustes.duracaoRapidoMin)
            }
            atualizar()
        }
    }

    private fun atualizar() {
        val tile = qsTile ?: return
        val ativa = (application as PlantaoApp).grafo.coordenador.ativasAgora().firstOrNull()
        tile.state = if (ativa != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Plantão"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (ativa != null) "Até ${Formato.hora(ativa.fim)}" else "Desligado"
        }
        tile.updateTile()
    }
}
