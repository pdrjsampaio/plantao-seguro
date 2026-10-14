package com.plantaoseguro.sirene

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.sentinela.SentinelaService
import com.plantaoseguro.sistema.Apps
import com.plantaoseguro.ui.tema.PlantaoTema
import kotlinx.coroutines.launch

/** Tela cheia do alerta: aparece por cima da tela bloqueada e acende o display. */
class AlarmeActivity : ComponentActivity() {

    private val grafo get() = (application as PlantaoApp).grafo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        if (grafo.central.tocando.value == null) {
            finish()
            return
        }
        val bloqueado = getSystemService(KeyguardManager::class.java).isKeyguardLocked

        setContent {
            PlantaoTema(escuro = true) {
                val alerta by grafo.central.tocando.collectAsStateWithLifecycle()
                LaunchedEffect(alerta) {
                    if (alerta == null) finish()
                }
                val a = alerta ?: return@PlantaoTema
                AlarmeTela(
                    alerta = a,
                    // Nome do grupo/contato só aparece com o celular desbloqueado.
                    mostrarConversa = !bloqueado,
                    volumePara = grafo.repo.dados.value.ajustes.volumeParaSirene,
                    onParar = {
                        grafo.central.confirmar("Você tocou em Parar na tela do alerta")
                        finish()
                    },
                    onAbrir = if (a.teste || a.pacote == null) null else ({ pararEAbrir(a) }),
                    onSilenciar = if (a.teste) null else ({
                        grafo.central.confirmar("Você silenciou a regra por 1 hora")
                        grafo.escopo.launch { grafo.coordenador.silenciarRegra(a.regraId, 60) }
                        finish()
                    })
                )
            }
        }
    }

    private fun pararEAbrir(a: AlertaAtivo) {
        grafo.central.confirmar("Você abriu a conversa pela tela do alerta")
        val abrir = {
            if (!SentinelaService.abrirConversa(this, a.chaves.keys.reversed())) {
                a.pacote?.let { Apps.abrir(this, it) }
            }
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    lifecycleScope.launch { abrir() }
                }

                override fun onDismissCancelled() {
                    finish()
                }
            })
        } else {
            abrir()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (volume && grafo.repo.dados.value.ajustes.volumeParaSirene && grafo.central.tocando.value != null) {
            grafo.central.confirmar("Você apertou um botão de volume")
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
