package com.plantaoseguro.servico

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.sentinela.SentinelaService
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano **só enquanto um plantão vale**. Mantém o processo
 * vivo (o sistema não fecha o app no meio da madrugada), mostra a
 * notificação fixa e cuida do vigia de volume. Fora do plantão não existe.
 */
class PlantaoService : Service() {

    private var vigia: VigiaVolume? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val grafo = (application as PlantaoApp).grafo
        val ativas = grafo.coordenador.ativasAgora()
        val principal = ativas.firstOrNull()
        val notificacao = grafo.notificador.notificacaoPlantao(
            principal?.plantao?.nome ?: "Plantão",
            principal?.fim ?: System.currentTimeMillis(),
            ativas.any { it.plantao.tocarSirene }
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notificador.ID_PLANTAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notificador.ID_PLANTAO, notificacao)
            }
        } catch (e: Exception) {
            Log.w("PlantaoSeguro", "Não consegui ficar em primeiro plano", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (principal == null) {
            // Reiniciado pelo sistema fora do plantão: não há o que manter.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            grafo.escopo.launch { grafo.coordenador.sincronizar("serviço reiniciado") }
            return START_NOT_STICKY
        }
        if (ativas.any { it.plantao.vigiarVolume }) {
            if (vigia == null) {
                vigia = VigiaVolume(this, grafo.repo, grafo.controleSom, grafo.coordenador, grafo.notificador).also { it.iniciar() }
            }
        } else {
            vigia?.parar()
            vigia = null
        }
        SentinelaService.pedirReconexao(this)
        rodando = true
        return START_STICKY
    }

    override fun onDestroy() {
        vigia?.parar()
        vigia = null
        rodando = false
        super.onDestroy()
    }

    companion object {
        /** O serviço do plantão está rodando agora (para o diagnóstico). */
        @Volatile
        var rodando: Boolean = false
            private set

        fun iniciar(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlantaoService::class.java))
            } catch (e: Exception) {
                // Android 12+ pode recusar se o app estiver em segundo plano sem isenção de bateria.
                Log.w("PlantaoSeguro", "Serviço do plantão não pôde subir agora", e)
            }
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, PlantaoService::class.java))
        }
    }
}
