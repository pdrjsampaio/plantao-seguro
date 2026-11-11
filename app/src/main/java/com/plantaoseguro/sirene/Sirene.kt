package com.plantaoseguro.sirene

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.plantaoseguro.modelo.ModoDuracao
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.Vibracao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import androidx.core.net.toUri

/**
 * Reprodução do alerta: som no canal de ALARME (toca mesmo no silencioso e no
 * vibrar), vibração, lanterna e trava de CPU. Um único alarme por vez.
 *
 * Não decide nada sobre alertas: só toca e avisa quando parou sozinho
 * ([aoTerminarSozinho]). Use sempre na thread principal.
 */
class Sirene(private val context: Context) {

    private val principal = Handler(Looper.getMainLooper())
    private val audio get() = context.getSystemService(AudioManager::class.java)

    /** Chamado quando o alerta termina por tempo ou por número de repetições. */
    var aoTerminarSozinho: (() -> Unit)? = null

    private val _previa = MutableStateFlow<SomRef?>(null)
    /** Som tocando na prévia da biblioteca de sons, se houver. */
    val previa: StateFlow<SomRef?> = _previa.asStateFlow()

    private var player: MediaPlayer? = null
    private var playerPrevia: MediaPlayer? = null
    private var vibrandoSozinho = false
    private var volumeAlarmeAntes = -1
    private var foco: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lanterna: Lanterna? = null
    private var passoRampa = 0
    private var repeticoesFeitas = 0
    private var toqueAtual: Toque? = null

    private val fimDoTempo = Runnable { terminarSozinho() }
    private val fimPrevia = Runnable { pararPrevia() }
    private val rampa = object : Runnable {
        override fun run() {
            val p = player ?: return
            passoRampa++
            val v = (0.15f + 0.85f * passoRampa / 20f).coerceAtMost(1f)
            runCatching { p.setVolume(v, v) }
            if (v < 1f) principal.postDelayed(this, 1000)
        }
    }

    fun tocando(): Boolean = player != null || vibrandoSozinho

    /** Começa o alerta. Devolve true quando som ou vibração de fato começaram. */
    fun tocar(toque: Toque): Boolean {
        if (tocando()) {
            renovar(toque)
            return true
        }
        pararPrevia()
        toqueAtual = toque
        repeticoesFeitas = 0

        if (!toque.tocarSom) {
            if (toque.vibracao == Vibracao.NENHUMA) return false
            if (!vibrar(toque.vibracao, repetir = true)) return false
            vibrandoSozinho = true
            iniciarComuns(toque, segundosLimite(toque, duracaoUmaVezSeg = 4))
            return true
        }

        val mp = criarPlayer(toque.som)
            ?: criarPlayer(SomRef.Embutido(Catalogo.padrao.id))
            ?: criarPlayerDoSistema()
            ?: return false

        mp.isLooping = toque.modoDuracao != ModoDuracao.REPETICOES
        if (toque.modoDuracao == ModoDuracao.REPETICOES) {
            mp.setOnCompletionListener { p ->
                repeticoesFeitas++
                if (repeticoesFeitas < toque.repeticoes.coerceIn(1, 50)) {
                    runCatching { p.seekTo(0); p.start() }
                } else {
                    terminarSozinho()
                }
            }
        }
        ajustarVolumeAlarme(toque.volume)
        pedirFoco()
        if (toque.crescente) {
            passoRampa = 0
            mp.setVolume(0.15f, 0.15f)
        } else {
            mp.setVolume(1f, 1f)
        }
        try {
            mp.start()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "O som não começou", e)
            runCatching { mp.release() }
            restaurarVolumeAlarme()
            soltarFoco()
            return false
        }
        player = mp
        if (toque.crescente) principal.postDelayed(rampa, 1000)
        if (toque.vibracao != Vibracao.NENHUMA) vibrar(toque.vibracao, repetir = true)
        val umaVez = (mp.duration / 1000).coerceAtLeast(1)
        iniciarComuns(toque, segundosLimite(toque, umaVez))
        // start() não falhou: o som está tocando. (isPlaying pode demorar alguns
        // milissegundos para virar true em alguns aparelhos; devolver false aqui
        // deixaria um som tocando sem tela para parar.)
        return true
    }

    /** Uma nova mensagem chegou durante o alerta: renova o tempo (não começa outro som). */
    fun renovar(toque: Toque) {
        if (!tocando()) return
        val atual = toqueAtual ?: toque
        if (atual.modoDuracao == ModoDuracao.REPETICOES) {
            repeticoesFeitas = 0
        } else {
            principal.removeCallbacks(fimDoTempo)
            principal.postDelayed(fimDoTempo, segundosLimite(atual, 4) * 1000L)
        }
    }

    /** Para tudo. Pode ser chamada de qualquer thread. */
    fun parar() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            principal.post { parar() }
            return
        }
        principal.removeCallbacks(fimDoTempo)
        principal.removeCallbacks(rampa)
        player?.let { p ->
            runCatching { p.setOnCompletionListener(null) }
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        vibrandoSozinho = false
        toqueAtual = null
        restaurarVolumeAlarme()
        soltarFoco()
        pararVibracao()
        lanterna?.parar()
        lanterna = null
        soltarCpu()
    }

    /** Uma vibração curta, para quando não dá para tocar (ex.: você está numa ligação). */
    fun vibrarCurto() {
        val v = vibrador() ?: return
        aplicarVibracao(v, VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
    }

    // ---------------------------------------------------------------- prévia

    fun previa(som: SomRef, volume: Int) {
        if (tocando()) return
        pararPrevia()
        val mp = criarPlayer(som) ?: return
        mp.isLooping = true
        ajustarVolumeAlarme(volume)
        pedirFoco()
        try {
            mp.start()
        } catch (e: IllegalStateException) {
            runCatching { mp.release() }
            restaurarVolumeAlarme()
            soltarFoco()
            return
        }
        playerPrevia = mp
        _previa.value = som
        principal.postDelayed(fimPrevia, 7000)
    }

    fun pararPrevia() {
        principal.removeCallbacks(fimPrevia)
        val p = playerPrevia ?: return
        playerPrevia = null
        runCatching { p.stop() }
        runCatching { p.release() }
        if (!tocando()) {
            restaurarVolumeAlarme()
            soltarFoco()
        }
        _previa.value = null
    }

    /** Vibra uma vez o padrão escolhido (para o editor de regras). */
    fun previaVibracao(v: Vibracao) {
        if (v == Vibracao.NENHUMA) return
        val vib = vibrador() ?: return
        aplicarVibracao(vib, VibrationEffect.createWaveform(padrao(v), -1))
    }

    // ---------------------------------------------------------------- interno

    private fun iniciarComuns(toque: Toque, limiteSeg: Int) {
        if (toque.lanterna) lanterna = Lanterna(context).also { it.iniciar() }
        segurarCpu(limiteSeg)
        principal.removeCallbacks(fimDoTempo)
        principal.postDelayed(fimDoTempo, limiteSeg * 1000L)
    }

    /** Quanto tempo no máximo o alerta toca, conforme o modo escolhido. */
    private fun segundosLimite(toque: Toque, duracaoUmaVezSeg: Int): Int = when (toque.modoDuracao) {
        ModoDuracao.TEMPO -> toque.duracaoSeg.coerceIn(5, 900)
        // Folga para as repetições acontecerem; o fim real vem do contador.
        ModoDuracao.REPETICOES -> (duracaoUmaVezSeg * toque.repeticoes.coerceIn(1, 50) + 10).coerceAtMost(LIMITE_SEGURANCA_SEG)
        ModoDuracao.ATE_PARAR -> LIMITE_SEGURANCA_SEG
    }

    private fun terminarSozinho() {
        if (!tocando()) return
        parar()
        aoTerminarSozinho?.invoke()
    }

    private val atributos: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun criarPlayer(som: SomRef): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(atributos)
            when (som) {
                is SomRef.Embutido -> {
                    val res = (Catalogo.porId(som.id) ?: Catalogo.padrao).res
                    context.resources.openRawResourceFd(res).use { afd ->
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                }
                is SomRef.Sistema -> mp.setDataSource(context, som.uri.toUri())
                is SomRef.Arquivo -> mp.setDataSource(context, som.uri.toUri())
            }
            mp.prepare()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "Não consegui preparar o som", e)
            runCatching { mp.release() }
            null
        }
    }

    private fun criarPlayerDoSistema(): MediaPlayer? {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return null
        return criarPlayer(SomRef.Sistema(uri.toString(), "Alarme do celular"))
    }

    private fun ajustarVolumeAlarme(volume: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (volumeAlarmeAntes < 0) volumeAlarmeAntes = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val alvo = (max * volume.coerceIn(10, 100) / 100f).roundToInt().coerceIn(1, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, alvo, 0) }
    }

    private fun restaurarVolumeAlarme() {
        if (volumeAlarmeAntes >= 0) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, volumeAlarmeAntes, 0) }
            volumeAlarmeAntes = -1
        }
    }

    private fun pedirFoco() {
        if (foco != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(atributos)
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { audio.requestAudioFocus(req) }
        foco = req
    }

    private fun soltarFoco() {
        foco?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        foco = null
    }

    private fun vibrador(): Vibrator? {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        return v?.takeIf { it.hasVibrator() }
    }

    private fun aplicarVibracao(v: Vibrator, efeito: VibrationEffect) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(efeito, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(efeito, atributos)
            }
        }
    }

    private fun vibrar(tipo: Vibracao, repetir: Boolean): Boolean {
        if (tipo == Vibracao.NENHUMA) return false
        val v = vibrador() ?: return false
        aplicarVibracao(v, VibrationEffect.createWaveform(padrao(tipo), if (repetir) 0 else -1))
        return true
    }

    private fun pararVibracao() {
        runCatching { vibrador()?.cancel() }
    }

    private fun segurarCpu(segundos: Int) {
        soltarCpu()
        val pm = context.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plantaoseguro:alerta").apply {
            setReferenceCounted(false)
            acquire((segundos + 15) * 1000L)
        }
    }

    private fun soltarCpu() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "PlantaoSeguro"

        /** "Até você parar" nunca passa disto, para não esgotar a bateria se ninguém ouvir. */
        const val LIMITE_SEGURANCA_SEG = 20 * 60

        /** Padrões de vibração: pausa, vibra, pausa, vibra... em milissegundos. */
        fun padrao(v: Vibracao): LongArray = when (v) {
            Vibracao.NENHUMA -> longArrayOf(0)
            Vibracao.PULSO -> longArrayOf(0, 700, 300, 700, 300, 1200, 900)
            Vibracao.CONTINUA -> longArrayOf(0, 2000, 150)
            Vibracao.BATIMENTO -> longArrayOf(0, 150, 120, 260, 900)
            Vibracao.RAPIDA -> longArrayOf(0, 200, 150)
            Vibracao.SOS -> longArrayOf(
                0, 200, 200, 200, 200, 200, 600,
                600, 200, 600, 200, 600, 600,
                200, 200, 200, 200, 200, 1400
            )
        }
    }
}

/** Pisca a lanterna enquanto o alerta toca (para lugares barulhentos). */
private class Lanterna(context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val id: String? = runCatching {
        camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private val h = Handler(Looper.getMainLooper())
    private var acesa = false
    private val pisca = object : Runnable {
        override fun run() {
            acesa = !acesa
            definir(acesa)
            h.postDelayed(this, if (acesa) 120L else 380L)
        }
    }

    fun iniciar() {
        if (id != null) h.post(pisca)
    }

    fun parar() {
        h.removeCallbacks(pisca)
        definir(false)
    }

    private fun definir(ligar: Boolean) {
        val c = id ?: return
        runCatching { camera.setTorchMode(c, ligar) }
    }
}
