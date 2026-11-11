package com.plantaoseguro.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoTest {

    @Test
    fun `normalizar tira acentos maiusculas e espacos extras`() {
        assertEquals("plantao uti adulto", Texto.normalizar("  Plantão   UTI\tAdulto "))
        assertEquals("acao urgente", Texto.normalizar("AÇÃO URGENTE"))
    }

    @Test
    fun `palavra precisa aparecer inteira`() {
        val c = Conteudo(titulo = "Dr. Silva", texto = "Paciente URGENTE no leito 3!!")
        assertTrue(Texto.casaPalavra(c, listOf("urgente")))
        assertTrue(Texto.casaPalavra(c, listOf("urgênte"))) // acento digitado errado ainda casa
        assertTrue(Texto.casaPalavra(c, listOf("leito 3")))
        assertFalse(Texto.casaPalavra(Conteudo(texto = "Resolva urgentemente"), listOf("urgente")))
        assertFalse(Texto.casaPalavra(c, listOf("emergência")))
    }

    @Test
    fun `palavra tambem casa no nome da conversa`() {
        val c = Conteudo(conversa = "Escala Urgente", texto = "Bom dia")
        assertTrue(Texto.casaPalavra(c, listOf("urgente")))
    }

    @Test
    fun `filtros vazios ou em branco nao casam nada`() {
        val c = Conteudo(titulo = "Qualquer", texto = "coisa")
        assertFalse(Texto.casaPalavra(c, listOf("", "   ")))
        assertFalse(Texto.casaContato(c, listOf("")))
    }

    @Test
    fun `contato casa por trecho no titulo na conversa ou no remetente`() {
        val grupo = Conteudo(titulo = "Plantão UTI Adulto: Maria", conversa = "Plantão UTI Adulto", remetentes = listOf("Maria"))
        assertTrue(Texto.casaContato(grupo, listOf("plantao uti")))
        assertTrue(Texto.casaContato(grupo, listOf("maria")))
        assertFalse(Texto.casaContato(grupo, listOf("Família")))
        val individual = Conteudo(titulo = "Coordenação Enfermagem", texto = "Pode vir?")
        assertTrue(Texto.casaContato(individual, listOf("coordenacao")))
    }

    @Test
    fun `simbolos na palavra nao quebram a busca`() {
        val c = Conteudo(texto = "Código (azul) acionado")
        assertTrue(Texto.casaPalavra(c, listOf("(azul)")))
        assertTrue(Texto.casaPalavra(c, listOf("código")))
    }
}
