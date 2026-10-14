package com.plantaoseguro.dados

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.plantaoseguro.modelo.Dados
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.VERSAO_DADOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

/**
 * Guarda regras, plantões, ajustes e o estado de execução num JSON local
 * (gravação atômica: ou o arquivo novo inteiro, ou o antigo intacto).
 *
 * Por que não DataStore: a escuta de notificações precisa da configuração
 * **na hora** em que o processo é recriado por uma notificação. Aqui a leitura
 * é síncrona e fica em memória; a decisão de tocar não espera disco.
 */
class Repositorio(private val arquivoDados: File) {

    constructor(context: Context) : this(File(context.filesDir, "dados.json"))

    private val arquivo = AtomicFile(arquivoDados)
    private val trava = Mutex()

    /** Preenchido quando o arquivo existia mas não pôde ser lido (o original é preservado). */
    var falhaAoLer: String? = null
        private set

    private val _dados = MutableStateFlow(carregar())
    val dados: StateFlow<Dados> = _dados.asStateFlow()

    suspend fun atualizar(mudanca: (Dados) -> Dados): Dados = trava.withLock {
        val novo = mudanca(_dados.value)
        if (novo != _dados.value) {
            _dados.value = novo
            withContext(Dispatchers.IO) { salvar(novo) }
        }
        novo
    }

    private fun carregar(): Dados = try {
        val texto = arquivo.openRead().use { it.readBytes().decodeToString() }
        corrigir(ler(texto))
    } catch (e: FileNotFoundException) {
        Dados()
    } catch (e: Exception) {
        // Não sobrescreve um arquivo que não conseguimos ler: guarda uma cópia.
        val copia = File(arquivoDados.parentFile, "dados.ilegivel-${System.currentTimeMillis()}.json")
        runCatching { arquivoDados.copyTo(copia, overwrite = true) }
        falhaAoLer = "Os dados salvos não puderam ser lidos. Uma cópia foi guardada em ${copia.name}."
        // Só o tipo do erro: a mensagem do leitor de JSON citaria trechos do arquivo.
        Log.e("PlantaoSeguro", "Não consegui ler os dados salvos: ${e.javaClass.simpleName}")
        Dados()
    }

    private fun salvar(dados: Dados) {
        val saida = arquivo.startWrite()
        try {
            saida.write(JSON.encodeToString(Dados.serializer(), dados).encodeToByteArray())
            arquivo.finishWrite(saida)
        } catch (e: Exception) {
            arquivo.failWrite(saida)
            Log.e("PlantaoSeguro", "Falha ao salvar os dados", e)
        }
    }

    companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }

        fun ler(texto: String): Dados = JSON.decodeFromString(Dados.serializer(), texto)

        /** Ajustes depois de ler um arquivo de versão anterior. */
        fun corrigir(d: Dados): Dados {
            var r = d
            if (r.regras.isEmpty()) r = r.copy(regras = listOf(Regra.padrao()))
            if (r.versao < VERSAO_DADOS) r = r.copy(versao = VERSAO_DADOS)
            return r
        }
    }
}
