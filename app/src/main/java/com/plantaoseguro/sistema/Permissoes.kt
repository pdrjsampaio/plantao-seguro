package com.plantaoseguro.sistema

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.plantaoseguro.sentinela.SentinelaService

/** O que o app precisa que a pessoa libere, em linguagem simples. */
enum class Requisito(val titulo: String, val porque: String) {
    NOTIFICACOES(
        "Mostrar notificações",
        "Para a sirene aparecer com o botão de parar e você saber quando o plantão começa."
    ),
    ESCUTA(
        "Acesso às notificações",
        "É como o app percebe que chegou mensagem. Ele vê só qual app avisou, nunca o texto."
    ),
    NAO_PERTURBE(
        "Controlar o Não Perturbe",
        "Para tirar o celular do silencioso e do Não Perturbe quando o plantão começa."
    ),
    TELA_CHEIA(
        "Abrir com a tela bloqueada",
        "Para a tela da sirene acender e aparecer mesmo com o celular bloqueado."
    ),
    BATERIA(
        "Bateria sem restrição",
        "Para o sistema não fechar o app no meio da madrugada."
    ),
    ALARMES(
        "Alarmes no horário exato",
        "Para o plantão começar e terminar no minuto certo."
    )
}

enum class Fabricante(val nome: String) {
    XIAOMI("Xiaomi, Redmi e POCO"),
    SAMSUNG("Samsung"),
    MOTOROLA("Motorola"),
    HUAWEI("Huawei e Honor"),
    OPPO("OPPO, realme e OnePlus"),
    VIVO("vivo"),
    ASUS("ASUS"),
    OUTRO("seu celular")
}

/** Um ajuste do fabricante que o Android não deixa o app conferir. */
data class Dica(val titulo: String, val passos: String, val atalhos: List<Intent>)

object Permissoes {

    /** Requisitos que existem nesta versão do Android. */
    fun aplicaveis(): List<Requisito> = Requisito.entries.filter {
        when (it) {
            Requisito.TELA_CHEIA -> Build.VERSION.SDK_INT >= 34
            Requisito.ALARMES -> Build.VERSION.SDK_INT >= 31
            else -> true
        }
    }

    fun ok(context: Context, r: Requisito): Boolean = when (r) {
        Requisito.NOTIFICACOES ->
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
        Requisito.ESCUTA -> SentinelaService.autorizada(context)
        Requisito.NAO_PERTURBE ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Requisito.TELA_CHEIA ->
            Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Requisito.BATERIA ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        Requisito.ALARMES ->
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun pendentes(context: Context): List<Requisito> = aplicaveis().filterNot { ok(context, it) }

    /** Telas de configuração do sistema para cada requisito, da mais direta para a mais genérica. */
    fun atalhos(context: Context, r: Requisito): List<Intent> {
        val pkg = context.packageName
        val pacoteUri = Uri.parse("package:$pkg")
        val detalhes = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacoteUri)
        return when (r) {
            Requisito.NOTIFICACOES -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                detalhes
            )
            Requisito.ESCUTA -> buildList {
                if (Build.VERSION.SDK_INT >= 30) {
                    add(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            SentinelaService.componente(context).flattenToString()
                        )
                    )
                }
                add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Requisito.NAO_PERTURBE -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), detalhes)
            Requisito.TELA_CHEIA -> buildList {
                if (Build.VERSION.SDK_INT >= 34) add(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pacoteUri))
                add(detalhes)
            }
            Requisito.BATERIA -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pacoteUri),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                detalhes
            )
            Requisito.ALARMES -> buildList {
                if (Build.VERSION.SDK_INT >= 31) add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pacoteUri))
                add(detalhes)
            }
        }
    }

    fun abrir(context: Context, r: Requisito): Boolean = abrirPrimeiro(context, atalhos(context, r))

    fun abrirPrimeiro(context: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                context.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // tenta o próximo
            }
        }
        return false
    }

    fun fabricante(): Fabricante = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> Fabricante.XIAOMI
        "samsung" -> Fabricante.SAMSUNG
        "motorola", "lenovo" -> Fabricante.MOTOROLA
        "huawei", "honor" -> Fabricante.HUAWEI
        "oppo", "realme", "oneplus" -> Fabricante.OPPO
        "vivo", "iqoo" -> Fabricante.VIVO
        "asus" -> Fabricante.ASUS
        else -> Fabricante.OUTRO
    }

    /** Ajustes que cada fabricante esconde e que o Android não deixa conferir. */
    fun dicas(context: Context, f: Fabricante = fabricante()): List<Dica> {
        val pkg = context.packageName
        val detalhes = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        fun comp(p: String, c: String) = Intent().setComponent(ComponentName(p, c))
        return when (f) {
            Fabricante.XIAOMI -> listOf(
                Dica(
                    "Inicialização automática",
                    "Ative o Plantão Seguro na lista, para ele voltar sozinho depois que o celular reiniciar.",
                    listOf(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), detalhes)
                ),
                Dica(
                    "Economia de bateria do app",
                    "Escolha Sem restrições.",
                    listOf(
                        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                            .putExtra("package_name", pkg)
                            .putExtra("package_label", "Plantão Seguro"),
                        detalhes
                    )
                ),
                Dica(
                    "Travar nos apps recentes",
                    "Abra os apps recentes, segure o card do Plantão Seguro e toque no cadeado.",
                    emptyList()
                )
            )
            Fabricante.SAMSUNG -> listOf(
                Dica(
                    "Apps que nunca entram em suspensão",
                    "Em Bateria, abra Limites de uso em segundo plano e adicione o Plantão Seguro em Apps que nunca entram em suspensão.",
                    listOf(
                        comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                        comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                        detalhes
                    )
                )
            )
            Fabricante.HUAWEI -> listOf(
                Dica(
                    "Inicialização de apps",
                    "Desligue o gerenciamento automático do Plantão Seguro e deixe as três opções ligadas.",
                    listOf(
                        comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                        detalhes
                    )
                )
            )
            Fabricante.OPPO -> listOf(
                Dica(
                    "Início automático",
                    "Permita que o Plantão Seguro inicie sozinho e funcione em segundo plano.",
                    listOf(
                        comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                        detalhes
                    )
                )
            )
            Fabricante.VIVO -> listOf(
                Dica(
                    "Segundo plano",
                    "Permita que o Plantão Seguro funcione em segundo plano e com alto consumo.",
                    listOf(
                        comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                        detalhes
                    )
                )
            )
            Fabricante.ASUS -> listOf(
                Dica(
                    "Início automático",
                    "Permita o início automático do Plantão Seguro.",
                    listOf(comp("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"), detalhes)
                )
            )
            Fabricante.MOTOROLA, Fabricante.OUTRO -> listOf(
                Dica(
                    "Bateria do app",
                    "Nos detalhes do app, abra Bateria e escolha Sem restrições.",
                    listOf(detalhes)
                )
            )
        }
    }
}
