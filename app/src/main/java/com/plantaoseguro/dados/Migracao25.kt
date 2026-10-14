package com.plantaoseguro.dados

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.plantaoseguro.modelo.AoTerminar
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.TipoAgenda
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Traz os plantões da versão publicada antes (2.5.0, banco Room
 * `plantao_seguro.db`) quando a v3 é instalada **por cima** dela.
 * Só acontece se o banco antigo existir na pasta do app, o que só ocorre
 * numa atualização assinada com a mesma chave. Lê, converte e não apaga nada.
 */
object Migracao25 {

    data class Resultado(val plantoes: List<Plantao>, val expediente: Boolean)

    fun ler(context: Context, agora: Long = System.currentTimeMillis(), zona: ZoneId = ZoneId.systemDefault()): Resultado? {
        val arquivo = context.getDatabasePath("plantao_seguro.db")
        if (!arquivo.exists()) return null
        return try {
            // Tratador de erro vazio: o padrão do Android apaga bancos corrompidos, e aqui nada é apagado.
            SQLiteDatabase.openDatabase(arquivo.path, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { }).use { db ->
                val plantoes = ArrayList<Plantao>()
                db.rawQuery(
                    "SELECT nome, inicioEpochMs, fimEpochMs, repetirSemanalmente, tirarDoSilencioso, desligarNaoPerturbe, status FROM plantoes",
                    null
                ).use { c ->
                    while (c.moveToNext()) {
                        val nome = c.getString(0)?.takeIf { it.isNotBlank() } ?: "Plantão"
                        val ini = c.getLong(1)
                        val fim = c.getLong(2)
                        val semanal = c.getInt(3) != 0
                        val status = c.getString(6) ?: ""
                        if (fim <= ini) continue
                        if (!semanal && (fim <= agora || status == "CANCELADO")) continue
                        val zIni = Instant.ofEpochMilli(ini).atZone(zona)
                        val zFim = Instant.ofEpochMilli(fim).atZone(zona)
                        plantoes += Plantao(
                            id = UUID.randomUUID().toString(),
                            nome = nome,
                            cor = plantoes.size % 6,
                            tipo = if (semanal) TipoAgenda.SEMANAL else TipoAgenda.UNICO,
                            dias = setOf(zIni.dayOfWeek.value),
                            inicioMin = zIni.hour * 60 + zIni.minute,
                            fimMin = zFim.hour * 60 + zFim.minute,
                            inicioMs = ini,
                            fimMs = fim,
                            tirarDoSilencioso = c.getInt(4) != 0,
                            desligarNaoPerturbe = c.getInt(5) != 0,
                            criadoEm = agora
                        )
                    }
                }
                var expediente = false
                runCatching {
                    db.rawQuery("SELECT modoTrabalhoAtivo FROM config_audio LIMIT 1", null).use { c ->
                        if (c.moveToFirst()) expediente = c.getInt(0) != 0
                    }
                }
                // A versão antiga criava uma linha nova a cada semana: junta as repetidas.
                val unicos = plantoes.distinctBy {
                    if (it.tipo == TipoAgenda.SEMANAL) "${it.nome}|${it.dias}|${it.inicioMin}|${it.fimMin}"
                    else "${it.nome}|${it.inicioMs}|${it.fimMs}"
                }.mapIndexed { i, p -> p.copy(cor = i % 6) }
                Resultado(unicos, expediente)
            }
        } catch (e: Exception) {
            Log.w("PlantaoSeguro", "Banco da versão anterior não pôde ser lido", e)
            null
        }
    }

    /** O antigo "Modo Trabalho" (seg a sex, 07:50 às 17:00) como um plantão comum. */
    fun expediente(agora: Long): Plantao = Plantao(
        id = UUID.randomUUID().toString(),
        nome = "Expediente",
        cor = 1,
        tipo = TipoAgenda.SEMANAL,
        dias = setOf(1, 2, 3, 4, 5),
        inicioMin = 7 * 60 + 50,
        fimMin = 17 * 60,
        tocarSirene = false,
        tirarDoSilencioso = true,
        desligarNaoPerturbe = true,
        vigiarVolume = true,
        aoTerminar = AoTerminar.MANTER,
        criadoEm = agora
    )
}
