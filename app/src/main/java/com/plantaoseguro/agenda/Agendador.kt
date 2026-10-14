package com.plantaoseguro.agenda

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.plantaoseguro.ui.MainActivity

/**
 * Programa o relógio do sistema para acordar o app: início/fim de plantão
 * (alarme de relógio, o que os fabricantes menos atrasam), insistência de
 * alerta não confirmado, verificação periódica da escuta durante o plantão
 * e volta do volume.
 */
class Agendador(private val context: Context) {

    private val alarmes get() = context.getSystemService(AlarmManager::class.java)

    fun podeAgendarExato(): Boolean = Build.VERSION.SDK_INT < 31 || alarmes.canScheduleExactAlarms()

    /** Próximo início/fim de plantão. Nulo cancela. */
    fun agendarTransicao(quando: Long?) {
        val pi = pi(REQ_TRANSICAO, AlarmeReceiver.ACAO_TRANSICAO)
        alarmes.cancel(pi)
        if (quando != null) agendar(quando, pi, Tipo.RELOGIO)
    }

    fun agendarInsistencia(chave: String, pacote: String, quando: Long) {
        agendar(quando, piInsistencia(chave, pacote), Tipo.EXATO)
    }

    fun cancelarInsistencia(chave: String) {
        alarmes.cancel(piInsistencia(chave, null))
    }

    /** Teste do alerta daqui a alguns segundos, para conferir com a tela bloqueada. */
    fun agendarTeste(quando: Long) {
        agendar(quando, pi(REQ_TESTE, AlarmeReceiver.ACAO_TESTE), Tipo.EXATO)
    }

    /** Conferência da escuta durante o plantão (aproximada, econômica). Nulo cancela. */
    fun agendarVerificacao(quando: Long?) {
        val pi = pi(REQ_VERIFICAR, AlarmeReceiver.ACAO_VERIFICAR)
        alarmes.cancel(pi)
        if (quando != null) agendar(quando, pi, Tipo.APROXIMADO)
    }

    /** Hora de voltar o volume depois de "manter baixo por X minutos". Nulo cancela. */
    fun agendarVoltaDoVolume(quando: Long?) {
        val pi = pi(REQ_VOLUME, AlarmeReceiver.ACAO_VOLTAR_VOLUME)
        alarmes.cancel(pi)
        if (quando != null) agendar(quando, pi, Tipo.EXATO)
    }

    private enum class Tipo { RELOGIO, EXATO, APROXIMADO }

    private fun pi(codigo: Int, acao: String): PendingIntent = PendingIntent.getBroadcast(
        context, codigo, Intent(context, AlarmeReceiver::class.java).setAction(acao), FLAGS
    )

    private fun piInsistencia(chave: String, pacote: String?): PendingIntent {
        val i = Intent(context, AlarmeReceiver::class.java)
            .setAction(AlarmeReceiver.ACAO_INSISTIR)
            .setData(Uri.parse("plantao://insistir/" + Uri.encode(chave)))
            .putExtra(AlarmeReceiver.EXTRA_CHAVE, chave)
            .putExtra(AlarmeReceiver.EXTRA_PACOTE, pacote)
        return PendingIntent.getBroadcast(context, REQ_INSISTIR, i, FLAGS)
    }

    private fun agendar(quando: Long, pi: PendingIntent, tipo: Tipo) {
        try {
            when {
                tipo == Tipo.APROXIMADO || !podeAgendarExato() ->
                    alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
                tipo == Tipo.RELOGIO ->
                    alarmes.setAlarmClock(AlarmManager.AlarmClockInfo(quando, abrirApp()), pi)
                else ->
                    alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
            }
        } catch (e: SecurityException) {
            Log.w("PlantaoSeguro", "Sem permissão de alarme exato; usando aproximado", e)
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }

    private fun abrirApp(): PendingIntent = PendingIntent.getActivity(
        context, REQ_ABRIR,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        FLAGS
    )

    private companion object {
        const val REQ_TRANSICAO = 100
        const val REQ_INSISTIR = 101
        const val REQ_TESTE = 102
        const val REQ_ABRIR = 103
        const val REQ_VERIFICAR = 104
        const val REQ_VOLUME = 105
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
