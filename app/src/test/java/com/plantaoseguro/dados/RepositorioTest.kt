package com.plantaoseguro.dados

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.TipoEvento
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositorioTest {

    private lateinit var pasta: File
    private val arquivo get() = File(pasta, "dados.json")

    @Before
    fun preparar() {
        pasta = File(ApplicationProvider.getApplicationContext<Context>().filesDir, "teste-${System.nanoTime()}").apply { mkdirs() }
    }

    @Test
    fun `primeira vez comeca com a regra padrao`() {
        val repo = Repositorio(arquivo)
        assertNull(repo.falhaAoLer)
        assertEquals(1, repo.dados.value.regras.size)
        assertFalse(repo.dados.value.ajustes.boasVindasConcluida)
    }

    @Test
    fun `o que foi salvo volta depois de reiniciar`() = runBlocking {
        val repo = Repositorio(arquivo)
        repo.atualizar { it.copy(plantoes = listOf(Plantao(id = "p", nome = "Noturno"))) }
        val depois = Repositorio(arquivo)
        assertEquals(listOf("Noturno"), depois.dados.value.plantoes.map { it.nome })
    }

    @Test
    fun `arquivo estragado nao e apagado`() {
        arquivo.writeText("{ isto não é json")
        val repo = Repositorio(arquivo)
        assertNotNull(repo.falhaAoLer)
        assertEquals(1, repo.dados.value.regras.size)
        // O original fica intacto e uma cópia é guardada para análise.
        assertEquals("{ isto não é json", arquivo.readText())
        assertTrue(pasta.listFiles()!!.any { it.name.startsWith("dados.ilegivel-") })
    }

    @Test
    fun `historico nunca passa do limite e pode ser apagado`() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val h = Historico(contexto) { 30 }
        Thread.sleep(300) // deixa a leitura inicial (em segundo plano) terminar
        repeat(Historico.MAXIMO + 10) { i ->
            h.registrar(TipoEvento.SISTEMA, Nivel.INFO, "Evento $i")
        }
        assertEquals(Historico.MAXIMO, h.eventos.value.size)
        assertEquals("Evento ${Historico.MAXIMO + 9}", h.eventos.value.first().titulo)
        h.limpar()
        assertTrue(h.eventos.value.isEmpty())
    }
}
