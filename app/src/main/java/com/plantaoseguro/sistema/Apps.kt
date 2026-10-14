package com.plantaoseguro.sistema

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppConhecido(val pacote: String, val nome: String)

/** Nomes, ícones e atalhos dos apps de mensagem. */
object Apps {

    val sugeridos = listOf(
        AppConhecido("com.whatsapp.w4b", "WhatsApp Business"),
        AppConhecido("com.whatsapp", "WhatsApp"),
        AppConhecido("org.telegram.messenger", "Telegram"),
        AppConhecido("com.microsoft.teams", "Microsoft Teams"),
        AppConhecido("com.Slack", "Slack"),
        AppConhecido("com.google.android.apps.messaging", "Mensagens (SMS)"),
        AppConhecido("com.samsung.android.messaging", "Mensagens Samsung"),
        AppConhecido("org.thoughtcrime.securesms", "Signal"),
        AppConhecido("com.google.android.gm", "Gmail"),
        AppConhecido("com.microsoft.office.outlook", "Outlook"),
        AppConhecido("com.discord", "Discord"),
        AppConhecido("com.google.android.apps.dynamite", "Google Chat")
    )

    fun instalado(context: Context, pacote: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(pacote, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun nome(context: Context, pacote: String): String = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
    } catch (e: Exception) {
        sugeridos.firstOrNull { it.pacote == pacote }?.nome ?: pacote
    }

    fun icone(context: Context, pacote: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(pacote).toBitmap(96, 96).asImageBitmap()
    } catch (e: Exception) {
        null
    }

    /** Todos os apps que aparecem na tela inicial, em ordem alfabética. */
    fun comIcone(context: Context): List<AppConhecido> {
        val pm = context.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val lista = pm.queryIntentActivities(i, 0)
        return lista.map { AppConhecido(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pacote != context.packageName }
            .distinctBy { it.pacote }
            .sortedBy { it.nome.lowercase() }
    }

    /** Abre o app (usado para ir direto à mensagem depois de parar a sirene). */
    fun abrir(context: Context, pacote: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pacote) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }
}
