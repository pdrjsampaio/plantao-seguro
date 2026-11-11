package com.plantaoseguro.dados

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plantaoseguro.modelo.TipoAgenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A 2.5.0 (APK v1.0.0) guardava os plantões num banco Room. Este teste monta
 * um banco com o mesmo esquema (observado no APK) e confere a importação.
 */
@RunWith(AndroidJUnit4::class)
class Migracao25Test {

    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val sp = ZoneId.of("America/Sao_Paulo")
    private fun ms(dia: Int, hora: Int, min: Int = 0) =
        LocalDateTime.of(2026, 10, dia, hora, min).atZone(sp).toInstant().toEpochMilli()

    private val agora get() = ms(14, 12)

    private fun criarBanco(bloco: SQLiteDatabase.() -> Unit) {
        val arquivo = contexto.getDatabasePath("plantao_seguro.db")
        arquivo.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            db.execSQL(
                "CREATE TABLE plantoes (id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, " +
                    "inicioEpochMs INTEGER NOT NULL, fimEpochMs INTEGER NOT NULL, repetirSemanalmente INTEGER NOT NULL, " +
                    "tirarDoSilencioso INTEGER NOT NULL, desligarNaoPerturbe INTEGER NOT NULL, status TEXT NOT NULL)"
            )
            db.execSQL("CREATE TABLE config_audio (id INTEGER PRIMARY KEY, modoTrabalhoAtivo INTEGER NOT NULL)")
            db.bloco()
        }
    }

    private fun SQLiteDatabase.plantao(nome: String, ini: Long, fim: Long, semanal: Boolean, status: String = "AGENDADO") =
        execSQL(
            "INSERT INTO plantoes (nome, inicioEpochMs, fimEpochMs, repetirSemanalmente, tirarDoSilencioso, desligarNaoPerturbe, status) VALUES (?, ?, ?, ?, 1, 0, ?)",
            arrayOf<Any>(nome, ini, fim, if (semanal) 1 else 0, status)
        )

    @Test
    fun `sem banco antigo nao importa nada`() {
        contexto.getDatabasePath("plantao_seguro.db").delete()
        assertNull(Migracao25.ler(contexto, agora, sp))
    }

    @Test
    fun `importa plantoes semanais e unicos e junta as repeticoes`() {
        criarBanco {
            // A 2.5.0 criava uma linha nova a cada semana para o mesmo plantão.
            plantao("Noturno", ms(5, 19), ms(6, 7), semanal = true)
            plantao("Noturno", ms(12, 19), ms(13, 7), semanal = true)
            plantao("Mutirão", ms(20, 8), ms(20, 18), semanal = false)
            plantao("Já passou", ms(1, 8), ms(1, 18), semanal = false)
            plantao("Cancelado", ms(21, 8), ms(21, 18), semanal = false, status = "CANCELADO")
            plantao("Errado", ms(22, 18), ms(22, 8), semanal = false)
            execSQL("INSERT INTO config_audio (id, modoTrabalhoAtivo) VALUES (1, 1)")
        }
        val r = Migracao25.ler(contexto, agora, sp)!!
        assertEquals(listOf("Noturno", "Mutirão"), r.plantoes.map { it.nome })
        val noturno = r.plantoes[0]
        assertEquals(TipoAgenda.SEMANAL, noturno.tipo)
        assertEquals(setOf(1), noturno.dias) // segunda-feira
        assertEquals(19 * 60, noturno.inicioMin)
        assertEquals(7 * 60, noturno.fimMin)
        assertTrue(noturno.tirarDoSilencioso)
        assertEquals(TipoAgenda.UNICO, r.plantoes[1].tipo)
        assertTrue(r.expediente)
        // O "Modo Trabalho" vira um plantão comum, sem alertas.
        val exp = Migracao25.expediente(agora)
        assertEquals(setOf(1, 2, 3, 4, 5), exp.dias)
        assertEquals(false, exp.tocarSirene)
    }

    @Test
    fun `banco estragado nao derruba o app`() {
        val arquivo = contexto.getDatabasePath("plantao_seguro.db")
        arquivo.parentFile?.mkdirs()
        arquivo.writeText("isto não é um banco")
        assertNull(Migracao25.ler(contexto, agora, sp))
        assertTrue("o arquivo antigo não pode ser apagado", arquivo.exists())
    }
}
