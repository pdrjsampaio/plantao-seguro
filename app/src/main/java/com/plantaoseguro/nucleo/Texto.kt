package com.plantaoseguro.nucleo

import java.text.Normalizer
import java.util.Locale

/** O que a notificação mostra. Só existe em memória, e só quando uma regra usa filtro. */
data class Conteudo(
    val titulo: String? = null,
    val conversa: String? = null,
    val texto: String? = null,
    val remetentes: List<String> = emptyList()
)

/** Comparação de textos tolerante a acentos, maiúsculas e espaços. */
object Texto {

    private val marcas = Regex("\\p{M}+")
    private val espacos = Regex("\\s+")

    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(marcas, "")
            .lowercase(Locale.ROOT)
            .replace(espacos, " ")
            .trim()

    /** Algum dos contatos/grupos aparece no nome da conversa, no título ou num remetente? */
    fun casaContato(c: Conteudo, contatos: List<String>): Boolean {
        val alvos = (listOfNotNull(c.conversa, c.titulo) + c.remetentes).map(::normalizar)
        return contatos.map(::normalizar).filter { it.isNotEmpty() }.any { k -> alvos.any { it.contains(k) } }
    }

    /** Alguma das palavras aparece (como palavra inteira) no título ou no texto? */
    fun casaPalavra(c: Conteudo, palavras: List<String>): Boolean {
        val texto = normalizar(listOfNotNull(c.conversa, c.titulo, c.texto).joinToString(" \n "))
        return palavras.map(::normalizar).filter { it.isNotEmpty() }.any { contemPalavra(texto, it) }
    }

    fun contemPalavra(textoNormalizado: String, palavraNormalizada: String): Boolean {
        val r = Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(palavraNormalizada) + "($|[^\\p{L}\\p{N}])")
        return r.containsMatchIn(textoNormalizado)
    }
}
