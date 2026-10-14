package com.plantaoseguro.agenda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.sentinela.SentinelaService
import kotlinx.coroutines.launch

/** Acorda na hora programada: início/fim de plantão, insistência, teste, verificação, volume. */
class AlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val grafo = (context.applicationContext as PlantaoApp).grafo
        val pendente = goAsync()
        grafo.escopo.launch {
            try {
                when (intent.action) {
                    ACAO_TRANSICAO -> grafo.coordenador.sincronizar("horário do plantão")
                    ACAO_INSISTIR -> grafo.coordenador.insistir(
                        intent.getStringExtra(EXTRA_CHAVE).orEmpty(),
                        intent.getStringExtra(EXTRA_PACOTE)
                    )
                    ACAO_TESTE -> grafo.coordenador.testar(null)
                    ACAO_VERIFICAR -> grafo.coordenador.verificarEscuta()
                    ACAO_VOLTAR_VOLUME -> grafo.coordenador.voltarVolume()
                }
            } finally {
                pendente.finish()
            }
        }
    }

    companion object {
        const val ACAO_TRANSICAO = "com.plantaoseguro.TRANSICAO"
        const val ACAO_INSISTIR = "com.plantaoseguro.INSISTIR"
        const val ACAO_TESTE = "com.plantaoseguro.TESTE"
        const val ACAO_VERIFICAR = "com.plantaoseguro.VERIFICAR"
        const val ACAO_VOLTAR_VOLUME = "com.plantaoseguro.VOLTAR_VOLUME"
        const val EXTRA_CHAVE = "chave"
        const val EXTRA_PACOTE = "pacote"
    }
}

/** Celular ligou, app atualizou, relógio ou fuso mudou: recalcula tudo. */
class SistemaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val grafo = (context.applicationContext as PlantaoApp).grafo
        val motivo = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "celular ligado"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app atualizado"
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> "relógio alterado"
            else -> "permissão de alarme alterada"
        }
        SentinelaService.pedirReconexao(context)
        val pendente = goAsync()
        grafo.escopo.launch {
            try {
                grafo.coordenador.sincronizar(motivo)
            } finally {
                pendente.finish()
            }
        }
    }
}
