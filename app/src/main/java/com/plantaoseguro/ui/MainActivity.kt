package com.plantaoseguro.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.ui.tema.PlantaoTema
import kotlinx.coroutines.launch

/** A única tela "normal" do app: hospeda a navegação em Compose. */
class MainActivity : ComponentActivity() {

    private val grafo get() = (application as PlantaoApp).grafo

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) lerDestino(intent)
        setContent {
            PlantaoTema {
                CompositionLocalProvider(LocalGrafo provides grafo) {
                    AppPlantao(destino = destinoPendente, onDestinoAberto = { destinoPendente = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerDestino(intent)
    }

    override fun onStart() {
        super.onStart()
        // Abrir o app também recalcula o estado: se algum horário foi perdido
        // (celular desligado, alarme atrasado), ele se corrige aqui.
        grafo.escopo.launch { grafo.coordenador.sincronizar("app aberto") }
    }

    /** Notificações do app podem pedir para abrir uma tela específica. */
    private fun lerDestino(i: Intent?) {
        val d = i?.getStringExtra(EXTRA_DESTINO) ?: return
        // A tela é exportada (é a do ícone), então só aceita destinos conhecidos.
        if (d in DESTINOS_EXTERNOS) destinoPendente = d
    }

    companion object {
        const val EXTRA_DESTINO = "com.plantaoseguro.extra.DESTINO"
        const val DESTINO_PERMISSOES = "permissoes"
        const val DESTINO_DIAGNOSTICO = "diagnostico"
        private val DESTINOS_EXTERNOS = setOf(DESTINO_PERMISSOES, DESTINO_DIAGNOSTICO)

        /** Tela a abrir assim que as abas aparecerem (notificação ou fim dos primeiros passos). */
        var destinoPendente by mutableStateOf<String?>(null)
    }
}
