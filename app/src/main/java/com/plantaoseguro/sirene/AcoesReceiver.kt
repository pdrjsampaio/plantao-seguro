package com.plantaoseguro.sirene

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.aviso.Notificador
import kotlinx.coroutines.launch

/** Botões das notificações: Parar, Silenciar 1 h, Testar, Encerrar plantão, volume. */
class AcoesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val grafo = (context.applicationContext as PlantaoApp).grafo
        when (intent.action) {
            Notificador.ACAO_CONFIRMAR -> grafo.central.confirmar("Você tocou em Parar na notificação")
            Notificador.ACAO_TESTAR -> grafo.coordenador.testar(null)
            else -> {
                val pendente = goAsync()
                grafo.escopo.launch {
                    try {
                        when (intent.action) {
                            Notificador.ACAO_SILENCIAR -> {
                                val id = intent.getStringExtra(Notificador.EXTRA_REGRA)
                                grafo.central.confirmar("Você silenciou a regra por 1 hora")
                                if (id != null) grafo.coordenador.silenciarRegra(id, 60)
                            }
                            Notificador.ACAO_ENCERRAR -> grafo.coordenador.encerrarAgora()
                            Notificador.ACAO_VOLUME_30 -> grafo.coordenador.manterVolumeBaixo(30)
                            Notificador.ACAO_VOLUME_60 -> grafo.coordenador.manterVolumeBaixo(60)
                            Notificador.ACAO_VOLUME_AGORA -> grafo.coordenador.voltarVolume()
                        }
                    } finally {
                        pendente.finish()
                    }
                }
            }
        }
    }
}
