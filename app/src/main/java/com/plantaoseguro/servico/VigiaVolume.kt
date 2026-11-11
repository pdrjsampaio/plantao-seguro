package com.plantaoseguro.servico

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.plantaoseguro.agenda.Coordenador
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.dados.Repositorio
import com.plantaoseguro.som.ControleSom

/**
 * Durante um plantão que "vigia o volume": se você baixar o volume de toque ou
 * de notificação abaixo do mínimo, o app pergunta por quanto tempo manter
 * (como na versão anterior). Só existe enquanto o serviço do plantão roda.
 */
class VigiaVolume(
    private val context: Context,
    private val repo: Repositorio,
    private val som: ControleSom,
    private val coordenador: Coordenador,
    private val notificador: Notificador
) {
    private val principal = Handler(Looper.getMainLooper())
    private var observador: ContentObserver? = null
    private var perguntou = false

    fun iniciar() {
        if (observador != null) return
        val o = object : ContentObserver(principal) {
            override fun onChange(selfChange: Boolean) = avaliar()
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, o)
        observador = o
    }

    fun parar() {
        observador?.let { context.contentResolver.unregisterContentObserver(it) }
        observador = null
        perguntou = false
        notificador.limparPerguntaVolume()
    }

    private fun avaliar() {
        val agora = System.currentTimeMillis()
        // Ignora as mudanças feitas pelo próprio app.
        if (agora - som.ultimaMudancaDoApp < 1_500) return
        val minimo = coordenador.ativasAgora(agora)
            .filter { it.plantao.vigiarVolume }
            .maxOfOrNull { it.plantao.volumeMinimo } ?: return
        if (repo.dados.value.execucao.volumeReduzidoAte > agora) return
        if (som.menorVolumePercentual() < minimo) {
            if (!perguntou) {
                perguntou = true
                notificador.perguntarVolume(minimo)
            }
        } else {
            perguntou = false
            notificador.limparPerguntaVolume()
        }
    }
}
