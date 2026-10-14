package com.plantaoseguro.sentinela

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.plantaoseguro.PlantaoApp
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.nucleo.Conteudo
import com.plantaoseguro.nucleo.Decisao
import com.plantaoseguro.nucleo.MotivoRemocao
import com.plantaoseguro.nucleo.Motor
import com.plantaoseguro.nucleo.Recebida
import com.plantaoseguro.sistema.Apps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A escuta das notificações (adaptador fino entre o Android e o motor).
 *
 * Privacidade, garantida aqui:
 *  1. Apps que nenhuma regra vigia são descartados na primeira linha.
 *  2. Título e texto da notificação só são lidos quando alguma regra daquele
 *     app tem filtro de contato ou palavra — e só na memória, para comparar.
 *     Nada disso é gravado, registrado em log ou enviado a lugar nenhum.
 *  3. Fora do plantão (e sem regras "sempre"), nada acontece: o app de
 *     mensagens toca o som dele normalmente.
 */
class SentinelaService : NotificationListenerService() {

    private val grafo get() = (application as PlantaoApp).grafo

    override fun onListenerConnected() {
        super.onListenerConnected()
        instancia = this
        _conectada.value = true
        // O que já estava na tela não é mensagem nova: marca como visto para
        // uma reapresentação (ex.: o WhatsApp reiniciou) não tocar o alerta.
        runCatching {
            activeNotifications?.forEach { grafo.central.memoria.registrar(it.key, it.notification?.`when` ?: 0L) }
        }
        grafo.escopo.launch { grafo.coordenador.sincronizar("escuta conectada") }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instancia === this) instancia = null
        _conectada.value = false
        pedirReconexao(this)
    }

    override fun onDestroy() {
        if (instancia === this) instancia = null
        _conectada.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        val pacote = s.packageName ?: return
        val agora = System.currentTimeMillis()

        if (pacote == packageName) {
            if (s.tag == Notificador.TAG_TESTE_ESCUTA) _ultimoTeste.value = agora
            return
        }
        val g = grafo
        val dados = g.repo.dados.value
        if (!Motor.monitora(dados.regras, pacote)) return
        _ultimaRecebida.value = agora

        val n = s.notification ?: return
        val conteudo = if (Motor.precisaConteudo(dados.regras, pacote, agora)) extrairConteudo(n) else null
        val recebida = Recebida(
            chave = s.key,
            pacote = pacote,
            quando = n.`when`,
            postadaEm = s.postTime,
            emAndamento = s.isOngoing,
            resumoDeGrupo = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            servicoPrimeiroPlano = (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            soAlertarUmaVez = (n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0,
            categoria = n.category,
            telaCheia = n.fullScreenIntent != null,
            conteudo = conteudo
        )
        val decisao = Motor.decidir(
            recebida, dados.regras, g.coordenador.ativasAgora(agora), dados.ajustes, g.central.memoria, agora
        )
        if (decisao !is Decisao.Alertar) return

        n.contentIntent?.let { intencoes[s.key] = it }
        if ((n.flags and Notification.FLAG_AUTO_CANCEL) != 0) autoCancelar += s.key

        val appNome = Apps.nome(this, pacote)
        if (dados.ajustes.silenciarEmLigacao && !decisao.chamada && !decisao.regra.critica && emLigacao()) {
            g.sirene.vibrarCurto()
            g.historico.registrar(
                TipoEvento.ALERTA, Nivel.INFO, "Mensagem durante uma ligação",
                "$appNome, regra ${decisao.regra.nome}. Só vibrou para não atrapalhar a ligação."
            )
            return
        }
        val conversa = if (decisao.regra.contatos.isNotEmpty()) conteudo?.conversa ?: conteudo?.titulo else null
        g.central.disparar(decisao, recebida, appNome, conversa)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        val s = sbn ?: return
        intencoes.remove(s.key)
        autoCancelar.remove(s.key)
        val g = grafo
        val dados = g.repo.dados.value
        if (!Motor.monitora(dados.regras, s.packageName ?: return)) return
        val chamada = g.central.tocando.value?.chamada == true
        val motivo = when (reason) {
            REASON_CLICK -> MotivoRemocao.TOCOU
            REASON_CANCEL, REASON_CANCEL_ALL, REASON_SNOOZED -> MotivoRemocao.DISPENSOU
            REASON_APP_CANCEL, REASON_APP_CANCEL_ALL, REASON_GROUP_SUMMARY_CANCELED ->
                if (chamada) MotivoRemocao.CHAMADA_ENCERRADA else MotivoRemocao.APP_REMOVEU
            else -> null
        }
        g.central.notificacaoRemovida(s.key, motivo, dados.ajustes.pararAoTocarNaMensagem)
    }

    private fun emLigacao(): Boolean {
        val modo = getSystemService(AudioManager::class.java).mode
        return modo == AudioManager.MODE_IN_CALL || modo == AudioManager.MODE_IN_COMMUNICATION
    }

    /** Lê nome da conversa, remetente e texto — só quando uma regra precisa comparar. */
    private fun extrairConteudo(n: Notification): Conteudo {
        val extras = n.extras
        val estilo = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val ultima = estilo?.messages?.lastOrNull()
        return Conteudo(
            titulo = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            conversa = estilo?.conversationTitle?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            texto = ultima?.text?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            remetentes = listOfNotNull(ultima?.person?.name?.toString())
        )
    }

    companion object {
        @Volatile
        private var instancia: SentinelaService? = null

        private val _conectada = MutableStateFlow(false)
        val conectada: StateFlow<Boolean> = _conectada.asStateFlow()

        private val _ultimaRecebida = MutableStateFlow(0L)
        /** Quando chegou a última notificação de um app vigiado (só o horário). */
        val ultimaRecebida: StateFlow<Long> = _ultimaRecebida.asStateFlow()

        private val _ultimoTeste = MutableStateFlow(0L)
        /** Quando a escuta viu a notificação de teste do diagnóstico. */
        val ultimoTeste: StateFlow<Long> = _ultimoTeste.asStateFlow()

        /** Atalho para abrir cada conversa (a intenção da notificação original), só em memória. */
        private val intencoes = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()
        private val autoCancelar: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        /** O acesso às notificações está liberado? (Pergunta direto ao sistema, sem cache.) */
        fun autorizada(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 27) {
                context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(componente(context))
            } else {
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }

        fun componente(context: Context): ComponentName = ComponentName(context, SentinelaService::class.java)

        /** Alguns celulares desligam a escuta sozinhos; isto pede para religar. */
        fun pedirReconexao(context: Context) {
            if (instancia == null && autorizada(context)) {
                runCatching { NotificationListenerService.requestRebind(componente(context)) }
            }
        }

        fun notificacaoAtiva(chave: String): Boolean =
            runCatching { instancia?.activeNotifications?.any { it.key == chave } }.getOrNull() ?: false

        /**
         * Abre a conversa de origem usando o atalho da própria notificação.
         * Deve ser chamada de uma tela visível (o Android só permite abrir
         * outro app a partir de algo que a pessoa tocou).
         */
        fun abrirConversa(context: Context, chaves: Collection<String>): Boolean {
            for (chave in chaves) {
                val pi = intencoes[chave] ?: continue
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val opcoes = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        pi.send(context, 0, null, null, null, null, opcoes.toBundle())
                    } else {
                        pi.send()
                    }
                    // Igual ao toque na notificação original: ela some se for do tipo que some.
                    if (chave in autoCancelar) runCatching { instancia?.cancelNotification(chave) }
                    return true
                } catch (e: Exception) {
                    Log.w("PlantaoSeguro", "Atalho da conversa não abriu", e)
                }
            }
            return false
        }
    }
}
