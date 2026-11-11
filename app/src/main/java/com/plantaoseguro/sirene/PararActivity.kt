package com.plantaoseguro.sirene

import android.app.Activity
import android.os.Bundle
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.sistema.Apps

/**
 * Tela invisível aberta ao tocar na notificação do alerta: confirma (para o
 * som) e leva direto para a conversa de origem, como se você tivesse tocado
 * na notificação do WhatsApp.
 */
class PararActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val grafo = (application as PlantaoApp).grafo
        val alerta = grafo.central.tocando.value ?: grafo.central.pendente.value
        grafo.central.confirmar("Você tocou na notificação do alerta")
        if (alerta != null && !alerta.teste) {
            val abriu = SentinelaService.abrirConversa(this, alerta.chaves.keys.reversed())
            if (!abriu) alerta.pacote?.let { Apps.abrir(this, it) }
        }
        finish()
    }
}
