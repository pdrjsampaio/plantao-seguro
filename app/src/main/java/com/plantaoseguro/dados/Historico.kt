package com.plantaoseguro.dados

import android.content.Context
import android.util.Log
import com.plantaoseguro.modelo.Evento
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.TipoEvento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Histórico do que o app fez: plantão começou, alerta tocou, quem confirmou.
 * Guarda só fatos — nunca o texto, o remetente ou o grupo das mensagens.
 * Mantém até [MAXIMO] eventos e apaga os mais velhos que [diasRetencao].
 */
class Historico(context: Context, private val diasRetencao: () -> Int = { 30 }) {

    private val arquivo = File(context.filesDir, "historico.jsonl")
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trava = Mutex()
    private val _eventos = MutableStateFlow<List<Evento>>(emptyList())

    /** Mais recentes primeiro. */
    val eventos: StateFlow<List<Evento>> = _eventos.asStateFlow()

    init {
        escopo.launch { carregar() }
    }

    fun registrar(
        tipo: TipoEvento,
        nivel: Nivel,
        titulo: String,
        detalhe: String? = null,
        quando: Long = System.currentTimeMillis()
    ) {
        val e = Evento(quando, tipo, nivel, titulo, detalhe)
        _eventos.update { (listOf(e) + it).take(MAXIMO) }
        escopo.launch {
            trava.withLock {
                try {
                    arquivo.appendText(Repositorio.JSON.encodeToString(Evento.serializer(), e) + "\n")
                    if (arquivo.length() > 700_000) compactar()
                } catch (ex: Exception) {
                    Log.e("PlantaoSeguro", "Falha ao gravar histórico", ex)
                }
            }
        }
    }

    fun limpar() {
        _eventos.value = emptyList()
        escopo.launch { trava.withLock { arquivo.delete() } }
    }

    private suspend fun carregar() = trava.withLock {
        val lidos = try {
            if (!arquivo.exists()) emptyList() else arquivo.readLines().mapNotNull { linha ->
                runCatching { Repositorio.JSON.decodeFromString(Evento.serializer(), linha) }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e("PlantaoSeguro", "Falha ao ler histórico", e)
            emptyList()
        }
        val limite = System.currentTimeMillis() - diasRetencao().coerceAtLeast(1) * 24L * 3_600_000L
        _eventos.update { atuais ->
            (atuais + lidos).distinct().filter { it.t >= limite }.sortedByDescending { it.t }.take(MAXIMO)
        }
        if (lidos.any { it.t < limite }) compactar()
    }

    private fun compactar() {
        val manter = _eventos.value.take(MAXIMO).sortedBy { it.t }
        val tmp = File(arquivo.parentFile, "historico.tmp")
        tmp.writeText(manter.joinToString("") {
            Repositorio.JSON.encodeToString(Evento.serializer(), it) + "\n"
        })
        tmp.renameTo(arquivo)
    }

    companion object {
        const val MAXIMO = 2000
    }
}
