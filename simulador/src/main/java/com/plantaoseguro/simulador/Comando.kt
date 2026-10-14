package com.plantaoseguro.simulador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

/**
 * Ordens vindas do teste:
 *
 *   am broadcast -n com.plantaoseguro.simulador/.Comando -f 32 --es acao postar --ei id 7 \
 *       --es texto Mensagem_msg7 --el quando 1760000000000 --es conversa Equipe_UTI --ez chamada false
 *
 * Os textos usam "_" no lugar de espaço (o shell do teste não aceita aspas).
 */
class Comando : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Mensagens", NotificationManager.IMPORTANCE_HIGH))
        val id = intent.getIntExtra("id", 1)
        when (intent.getStringExtra("acao") ?: "postar") {
            "postar" -> nm.notify(id, montar(context, intent, id))
            "cancelar" -> nm.cancel(id)
            "cancelar_tudo" -> nm.cancelAll()
        }
    }

    private fun texto(intent: Intent, chave: String, padrao: String) =
        (intent.getStringExtra(chave) ?: padrao).replace('_', ' ')

    private fun montar(context: Context, intent: Intent, id: Int): android.app.Notification {
        val quando = intent.getLongExtra("quando", System.currentTimeMillis())
        val conversa = texto(intent, "conversa", "Equipe UTI")
        val remetente = texto(intent, "remetente", "Colega")
        val mensagem = texto(intent, "texto", "Mensagem de teste")
        val chamada = intent.getBooleanExtra("chamada", false)
        val umaVez = intent.getBooleanExtra("uma_vez", false)

        val abrir = PendingIntent.getActivity(
            context, id,
            Intent(context, Conversa::class.java).putExtra("id", id).putExtra("conversa", conversa)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val eu = Person.Builder().setName("Eu").build()
        val estilo = NotificationCompat.MessagingStyle(eu)
            .setConversationTitle(conversa)
            .setGroupConversation(true)
            .addMessage(mensagem, quando, Person.Builder().setName(remetente).build())

        val b = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(conversa)
            .setContentText(mensagem)
            .setWhen(quando)
            .setShowWhen(true)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setOnlyAlertOnce(umaVez)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (chamada) {
            b.setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentText("Chamada de voz")
                .setFullScreenIntent(abrir, true)
                .setOngoing(true)
        } else {
            b.setCategory(NotificationCompat.CATEGORY_MESSAGE).setStyle(estilo)
        }
        return b.build()
    }

    private companion object {
        const val CANAL = "mensagens"
    }
}
