package com.plantaoseguro.aviso

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.plantaoseguro.R
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.sirene.AcoesReceiver
import com.plantaoseguro.sirene.AlarmeActivity
import com.plantaoseguro.sirene.PararActivity
import com.plantaoseguro.ui.MainActivity
import com.plantaoseguro.util.Formato
import androidx.core.net.toUri

/**
 * Todas as notificações do app, num lugar só.
 * O som do alerta não vem da notificação: vem do player, no volume de alarme.
 */
class Notificador(private val context: Context) {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    fun podeNotificar(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** O canal do alerta continua com importância alta? (Se a pessoa baixar, a tela cheia some.) */
    fun canalAlertaOk(): Boolean =
        (nm.getNotificationChannel(CANAL_ALERTA)?.importance ?: NotificationManager.IMPORTANCE_HIGH) >=
            NotificationManager.IMPORTANCE_HIGH

    fun criarCanais() {
        val alerta = NotificationChannel(CANAL_ALERTA, "Alerta tocando", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aparece enquanto o alerta toca, com o botão de parar."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setBypassDnd(true)
        }
        val pendente = NotificationChannel(CANAL_PENDENTE, "Alertas sem confirmação", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quando um alerta toca até o fim e ninguém para."
            setSound(null, null)
        }
        val plantao = NotificationChannel(CANAL_PLANTAO, "Plantão em andamento", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Fica visível enquanto o plantão está valendo."
            setShowBadge(false)
        }
        val avisos = NotificationChannel(CANAL_AVISOS, "Início e fim do plantão", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisa quando o plantão começa e quando termina."
            setSound(null, null)
        }
        val problemas = NotificationChannel(CANAL_PROBLEMAS, "Problemas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa quando algo pode impedir o alerta de tocar."
        }
        val teste = NotificationChannel(CANAL_TESTE, "Teste da escuta", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notificação de teste usada pelo diagnóstico."
            setSound(null, null)
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(alerta, pendente, plantao, avisos, problemas, teste))
    }

    // ---------------------------------------------------------------- intents

    private fun abrirApp(destino: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (destino != null) {
            i.putExtra(MainActivity.EXTRA_DESTINO, destino)
            i.data = "plantao://abrir/$destino".toUri()
        }
        return PendingIntent.getActivity(context, if (destino == null) 1 else 5, i, FLAGS)
    }

    private fun telaAlarme(): PendingIntent {
        val i = Intent(context, AlarmeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(context, 2, i, FLAGS)
    }

    /** Toque na notificação do alerta: para e (se possível) abre a conversa de origem. */
    private fun pararEAbrir(codigo: Int): PendingIntent {
        val i = Intent(context, PararActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, codigo, i, FLAGS)
    }

    private fun acao(acao: String, codigo: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(context, AcoesReceiver::class.java).setAction(acao).apply(extras)
        return PendingIntent.getBroadcast(context, codigo, i, FLAGS)
    }

    // ---------------------------------------------------------------- alerta

    fun mostrarAlerta(a: AlertaAtivo) {
        val titulo = when {
            a.teste -> "Teste do alerta"
            a.chamada -> "Chamada de trabalho"
            a.quantidade > 1 -> "${a.quantidade} mensagens de trabalho"
            else -> "Mensagem de trabalho"
        }
        val texto = when {
            a.teste -> "Se você está ouvindo, o alerta funciona. Toque para parar."
            a.appNome != null -> "Chegou no ${a.appNome}. Toque para parar e abrir."
            else -> "Toque para parar."
        }
        val publica = NotificationCompat.Builder(context, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_SINAL)
            .setContentTitle(titulo)
            .setContentText("Toque para parar.")
            .build()
        val b = NotificationCompat.Builder(context, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_SINAL)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setSubText(if (a.teste) null else a.regraNome)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(a.inicio)
            .setShowWhen(true)
            .setContentIntent(pararEAbrir(3))
            .setFullScreenIntent(telaAlarme(), true)
            .addAction(0, "Parar", acao(ACAO_CONFIRMAR, 10))
        if (!a.teste && a.pacote != null) {
            b.addAction(0, "Abrir conversa", pararEAbrir(4))
            b.addAction(0, "Silenciar 1 h", acao(ACAO_SILENCIAR, 13) { putExtra(EXTRA_REGRA, a.regraId) })
        }
        nm.notify(ID_ALERTA, b.build())
    }

    fun limparAlerta() = nm.cancel(ID_ALERTA)

    fun mostrarNaoConfirmado(a: AlertaAtivo) {
        val quando = Formato.hora(a.inicio)
        val origem = a.appNome?.let { "no $it" } ?: ""
        val n = NotificationCompat.Builder(context, CANAL_PENDENTE)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_ALARME)
            .setContentTitle("Alerta sem confirmação")
            .setContentText("Mensagem $origem às $quando. O alerta tocou até o fim e ninguém parou.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Mensagem $origem às $quando. O alerta tocou até o fim e ninguém parou." +
                        if (a.toque.insistirMin > 0) " Vai tocar de novo em ${a.toque.insistirMin} min se a mensagem continuar sem ser aberta." else ""
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pararEAbrir(6))
            .addAction(0, "Já vi", acao(ACAO_CONFIRMAR, 14))
            .setAutoCancel(false)
            .build()
        nm.notify(ID_PENDENTE, n)
    }

    fun limparNaoConfirmado() = nm.cancel(ID_PENDENTE)

    // ---------------------------------------------------------------- plantão

    fun notificacaoPlantao(nome: String, ate: Long, comAlerta: Boolean): Notification =
        NotificationCompat.Builder(context, CANAL_PLANTAO)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_SINAL)
            .setContentTitle("Em plantão: $nome")
            .setContentText(
                if (comAlerta) "Alertas ligados até ${Formato.hora(ate)}"
                else "Som do celular garantido até ${Formato.hora(ate)}"
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(abrirApp())
            .addAction(0, "Testar alerta", acao(ACAO_TESTAR, 12))
            .addAction(0, "Encerrar plantão", acao(ACAO_ENCERRAR, 11))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun atualizarPlantao(nome: String, ate: Long, comAlerta: Boolean) {
        nm.notify(ID_PLANTAO, notificacaoPlantao(nome, ate, comAlerta))
    }

    fun limparPlantao() = nm.cancel(ID_PLANTAO)

    // ---------------------------------------------------------------- volume

    /** Você baixou o volume durante um plantão que vigia o volume. */
    fun perguntarVolume(volumeMinimo: Int) {
        val n = NotificationCompat.Builder(context, CANAL_PROBLEMAS)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_SINAL)
            .setContentTitle("Volume baixo durante o plantão")
            .setContentText("Por quanto tempo manter assim? Depois volta para $volumeMinimo%.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abrirApp())
            .addAction(0, "30 min", acao(ACAO_VOLUME_30, 20))
            .addAction(0, "1 hora", acao(ACAO_VOLUME_60, 21))
            .addAction(0, "Voltar agora", acao(ACAO_VOLUME_AGORA, 22))
            .build()
        nm.notify(ID_VOLUME, n)
    }

    fun limparPerguntaVolume() = nm.cancel(ID_VOLUME)

    // ---------------------------------------------------------------- avisos

    fun aviso(titulo: String, texto: String) {
        val n = NotificationCompat.Builder(context, CANAL_AVISOS)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_SINAL)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(abrirApp())
            .build()
        nm.notify(ID_AVISO, n)
    }

    fun problema(titulo: String, texto: String) {
        val n = NotificationCompat.Builder(context, CANAL_PROBLEMAS)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setColor(COR_ALARME)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(abrirApp(MainActivity.DESTINO_DIAGNOSTICO))
            .build()
        nm.notify(ID_PROBLEMA, n)
    }

    fun limparProblema() = nm.cancel(ID_PROBLEMA)

    /** Notificação usada pelo teste ponta a ponta da escuta. */
    fun postarTesteDeEscuta() {
        val n = NotificationCompat.Builder(context, CANAL_TESTE)
            .setSmallIcon(R.drawable.ic_stat_sirene)
            .setContentTitle("Teste da escuta")
            .setContentText("Pode ignorar: é o diagnóstico do Plantão Seguro.")
            .setTimeoutAfter(15_000)
            .setSilent(true)
            .build()
        nm.notify(TAG_TESTE_ESCUTA, ID_TESTE_ESCUTA, n)
    }

    fun limparTesteDeEscuta() = nm.cancel(TAG_TESTE_ESCUTA, ID_TESTE_ESCUTA)

    companion object {
        const val CANAL_ALERTA = "alerta"
        const val CANAL_PENDENTE = "pendente"
        const val CANAL_PLANTAO = "plantao"
        const val CANAL_AVISOS = "avisos"
        const val CANAL_PROBLEMAS = "problemas"
        const val CANAL_TESTE = "teste"

        const val ID_PLANTAO = 2001
        const val ID_ALERTA = 2002
        const val ID_AVISO = 2003
        const val ID_PROBLEMA = 2004
        const val ID_PENDENTE = 2005
        const val ID_VOLUME = 2006
        const val ID_TESTE_ESCUTA = 2007
        const val TAG_TESTE_ESCUTA = "teste-escuta"

        const val ACAO_CONFIRMAR = "com.plantaoseguro.CONFIRMAR"
        const val ACAO_SILENCIAR = "com.plantaoseguro.SILENCIAR"
        const val ACAO_ENCERRAR = "com.plantaoseguro.ENCERRAR_PLANTAO"
        const val ACAO_TESTAR = "com.plantaoseguro.TESTAR"
        const val ACAO_VOLUME_30 = "com.plantaoseguro.VOLUME_30"
        const val ACAO_VOLUME_60 = "com.plantaoseguro.VOLUME_60"
        const val ACAO_VOLUME_AGORA = "com.plantaoseguro.VOLUME_AGORA"
        const val EXTRA_REGRA = "regra"

        const val COR_SINAL = 0xFFFF6A13.toInt()
        const val COR_ALARME = 0xFFE0262F.toInt()

        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
