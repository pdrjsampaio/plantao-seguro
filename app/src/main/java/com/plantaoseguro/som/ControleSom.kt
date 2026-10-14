package com.plantaoseguro.som

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.plantaoseguro.modelo.SomSalvo

/**
 * Lê e muda o modo de som do celular: silencioso, Não Perturbe e volumes.
 * Antes do plantão o estado é salvo; no fim, pode ser restaurado do jeito
 * que estava.
 */
class ControleSom(private val context: Context) {

    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notificacoes get() = context.getSystemService(NotificationManager::class.java)

    /** O app tem permissão para mexer no Não Perturbe (e sair do silencioso)? */
    fun temAcessoNaoPerturbe(): Boolean = notificacoes.isNotificationPolicyAccessGranted

    fun emSilencioso(): Boolean = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL

    fun naoPerturbeLigado(): Boolean =
        notificacoes.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            notificacoes.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    fun salvar(): SomSalvo = SomSalvo(
        modoCampainha = audio.ringerMode,
        filtroInterrupcao = notificacoes.currentInterruptionFilter,
        volToque = audio.getStreamVolume(AudioManager.STREAM_RING),
        volNotificacao = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
        salvoEm = System.currentTimeMillis()
    )

    /** Sai do silencioso/vibrar e garante que o volume não está zerado. Devolve se conseguiu. */
    fun tirarDoSilencioso(): Boolean {
        ultimaMudancaDoApp = System.currentTimeMillis()
        try {
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            garantirVolume(AudioManager.STREAM_RING)
            garantirVolume(AudioManager.STREAM_NOTIFICATION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para sair do silencioso", e)
        }
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    /** Desliga o Não Perturbe. Devolve se ficou desligado. */
    fun desligarNaoPerturbe(): Boolean {
        if (!naoPerturbeLigado()) return true
        if (!temAcessoNaoPerturbe()) return false
        return try {
            notificacoes.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            !naoPerturbeLigado()
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão para desligar o Não Perturbe", e)
            false
        }
    }

    /** Volta o som para como estava antes do plantão. */
    fun restaurar(s: SomSalvo) {
        ultimaMudancaDoApp = System.currentTimeMillis()
        try {
            audio.setStreamVolume(AudioManager.STREAM_RING, s.volToque, 0)
            audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, s.volNotificacao, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Não consegui restaurar volumes", e)
        }
        if (temAcessoNaoPerturbe() &&
            s.filtroInterrupcao != NotificationManager.INTERRUPTION_FILTER_UNKNOWN &&
            notificacoes.currentInterruptionFilter != s.filtroInterrupcao
        ) {
            runCatching { notificacoes.setInterruptionFilter(s.filtroInterrupcao) }
        }
        if (audio.ringerMode != s.modoCampainha) {
            try {
                audio.ringerMode = s.modoCampainha
            } catch (e: SecurityException) {
                Log.w(TAG, "Não consegui voltar ao modo de campainha anterior", e)
            }
        }
    }

    fun descricaoAtual(): String {
        val modo = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "silencioso"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrar"
            else -> "som normal"
        }
        return if (naoPerturbeLigado()) "$modo, Não Perturbe ligado" else modo
    }

    /** Menor volume, em %, entre toque e notificação (o que o vigia de volume observa). */
    fun menorVolumePercentual(): Int {
        fun pct(s: Int): Int {
            val max = audio.getStreamMaxVolume(s).coerceAtLeast(1)
            return audio.getStreamVolume(s) * 100 / max
        }
        return minOf(pct(AudioManager.STREAM_RING), pct(AudioManager.STREAM_NOTIFICATION))
    }

    /** Sobe toque e notificação até pelo menos [percentual]. Não abaixa nada. */
    fun subirVolumePara(percentual: Int) {
        ultimaMudancaDoApp = System.currentTimeMillis()
        for (s in intArrayOf(AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION)) {
            val max = audio.getStreamMaxVolume(s)
            val alvo = (max * percentual.coerceIn(0, 100) / 100f).toInt().coerceIn(1, max)
            if (audio.getStreamVolume(s) < alvo) runCatching { audio.setStreamVolume(s, alvo, 0) }
        }
    }

    /** Quando o próprio app mexeu no volume (para o vigia não confundir com você). */
    @Volatile
    var ultimaMudancaDoApp: Long = 0L
        private set

    private fun garantirVolume(stream: Int) {
        ultimaMudancaDoApp = System.currentTimeMillis()
        val max = audio.getStreamMaxVolume(stream)
        if (audio.getStreamVolume(stream) == 0) {
            audio.setStreamVolume(stream, (max * 0.6f).toInt().coerceAtLeast(1), 0)
        }
    }

    private companion object {
        const val TAG = "PlantaoSeguro"
    }
}
