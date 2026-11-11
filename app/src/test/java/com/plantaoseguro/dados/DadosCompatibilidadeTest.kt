package com.plantaoseguro.dados

import com.plantaoseguro.modelo.AoTerminar
import com.plantaoseguro.modelo.Dados
import com.plantaoseguro.modelo.ModoDuracao
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.QuandoVale
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.modelo.VERSAO_DADOS
import com.plantaoseguro.modelo.Vibracao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O arquivo de configurações precisa continuar legível nas próximas versões
 * e o app precisa aguentar arquivos de versões futuras (campos a mais).
 */
class DadosCompatibilidadeTest {

    /** Exemplo real do formato 3 (gravado pela 3.0.0). Não mude: é o contrato com quem já instalou. */
    private val formato3 = """
        {
          "versao": 3,
          "regras": [
            {
              "id": "padrao", "nome": "Mensagens de trabalho", "ligada": true,
              "apps": ["com.whatsapp.w4b"], "contatos": ["Plantão UTI"], "palavras": [], "ignorar": ["bom dia"],
              "quando": "NO_PLANTAO", "plantoes": [], "chamadas": true, "critica": false,
              "toque": {
                "tocarSom": true, "som": {"type": "embutido", "id": "sirene_europeia"}, "volume": 80,
                "crescente": true, "vibracao": "SOS", "lanterna": false, "modoDuracao": "ATE_PARAR",
                "duracaoSeg": 60, "repeticoes": 3, "insistirMin": 10
              },
              "silenciadaAte": 0, "criadaEm": 1760000000000
            }
          ],
          "plantoes": [
            {
              "id": "noite", "nome": "Noturno", "cor": 1, "tipo": "SEMANAL", "dias": [1, 3, 5],
              "inicioMin": 1140, "fimMin": 420, "inicioMs": 0, "fimMs": 0, "ligado": true,
              "tocarSirene": true, "tirarDoSilencioso": true, "desligarNaoPerturbe": false,
              "vigiarVolume": true, "volumeMinimo": 60, "aoTerminar": "MANTER", "rapido": false, "criadoEm": 0
            }
          ],
          "ajustes": {"pararAoTocarNaMensagem": false, "diasHistorico": 90, "boasVindasConcluida": true},
          "execucao": {"pulos": [{"plantaoId": "noite", "inicioOcorrencia": 1760050000000}], "migracao25Feita": true}
        }
    """.trimIndent()

    @Test
    fun `formato 3 continua legivel`() {
        val d = Repositorio.corrigir(Repositorio.ler(formato3))
        val r = d.regras.single()
        assertEquals(listOf("Plantão UTI"), r.contatos)
        assertEquals(SomRef.Embutido("sirene_europeia"), r.toque.som)
        assertEquals(Vibracao.SOS, r.toque.vibracao)
        assertEquals(ModoDuracao.ATE_PARAR, r.toque.modoDuracao)
        assertEquals(10, r.toque.insistirMin)
        val p = d.plantoes.single()
        assertEquals(setOf(1, 3, 5), p.dias)
        assertEquals(AoTerminar.MANTER, p.aoTerminar)
        assertTrue(p.vigiarVolume)
        assertFalse(d.ajustes.pararAoTocarNaMensagem)
        assertEquals(90, d.ajustes.diasHistorico)
        assertTrue(d.ajustes.ignorarAnteriores) // ausente no arquivo: valor padrão
        assertEquals(1, d.execucao.pulos.size)
    }

    @Test
    fun `campos de versoes futuras sao ignorados e valores desconhecidos voltam ao padrao`() {
        val futuro = """
            {"versao": 9, "novidade": {"x": 1},
             "regras": [{"id": "a", "nome": "A", "quando": "EM_REUNIAO", "campoNovo": true,
                         "toque": {"vibracao": "VIBRACAO_NOVA", "som": {"type": "embutido", "id": "gota"}}}],
             "plantoes": [{"id": "p", "nome": "P", "tipo": "MENSAL"}]}
        """.trimIndent()
        val d = Repositorio.ler(futuro)
        assertEquals(QuandoVale.NO_PLANTAO, d.regras.single().quando)
        assertEquals(Vibracao.PULSO, d.regras.single().toque.vibracao)
        assertEquals(TipoAgenda.SEMANAL, d.plantoes.single().tipo)
        assertEquals(9, d.versao)
    }

    @Test
    fun `arquivo minimo ganha a regra padrao`() {
        val d = Repositorio.corrigir(Repositorio.ler("""{"versao": 1, "regras": []}"""))
        assertEquals(listOf(Regra.ID_PADRAO), d.regras.map { it.id })
        assertEquals(VERSAO_DADOS, d.versao)
        assertTrue(d.plantoes.isEmpty())
    }

    @Test
    fun `o tipo do som fica gravado com nomes estaveis`() {
        val json = Repositorio.JSON.encodeToString(Dados.serializer(), Dados())
        assertTrue(json.contains("\"type\":\"embutido\""))
        val arquivo = Toque(som = SomRef.Arquivo("content://x/1", "Meu som"))
        val ida = Repositorio.JSON.encodeToString(Toque.serializer(), arquivo)
        assertTrue(ida.contains("\"type\":\"arquivo\""))
        assertEquals(arquivo, Repositorio.JSON.decodeFromString(Toque.serializer(), ida))
    }

    // ------------------------------------------------------------ cópia de segurança

    private val dadosCompletos = Dados(
        regras = listOf(
            Regra.padrao().copy(silenciadaAte = 1_900_000_000_000L),
            Regra(id = "b", nome = "Chefia", toque = Toque(som = SomRef.Arquivo("content://audio/9", "Sirene do Zé")))
        ),
        plantoes = listOf(
            Plantao(id = "noite", nome = "Noturno"),
            Plantao(id = "r", nome = "Plantão agora", tipo = TipoAgenda.UNICO, inicioMs = 1, fimMs = 2, rapido = true)
        )
    )

    @Test
    fun `copia de configuracoes vai e volta`() {
        val texto = Backup.exportar(dadosCompletos, "3.0.0", agora = 1_760_000_000_000L)
        val (novos, resultado) = Backup.importar(texto, Dados())
        resultado as Backup.Resultado.Ok
        assertEquals(2, resultado.regras)
        assertEquals(1, resultado.plantoes) // o plantão rápido não vai na cópia
        assertEquals(1, resultado.sonsTrocados) // som de arquivo não passa de um celular para outro
        novos!!
        assertEquals(0L, novos.regras.first().silenciadaAte)
        assertEquals(SomRef.Embutido("sirene_classica"), novos.regras[1].toque.som)
        assertEquals(listOf("noite"), novos.plantoes.map { it.id })
        assertTrue(novos.ajustes.boasVindasConcluida)
    }

    @Test
    fun `arquivo que nao e copia e recusado`() {
        val (d1, r1) = Backup.importar("isto não é json", Dados())
        assertNull(d1)
        assertTrue(r1 is Backup.Resultado.Erro)
        val (d2, r2) = Backup.importar("""{"formato": "outro-app/config", "regras": []}""", Dados())
        assertNull(d2)
        assertTrue(r2 is Backup.Resultado.Erro)
    }

    @Test
    fun `copia nao leva o historico nem o estado de execucao`() {
        val texto = Backup.exportar(dadosCompletos, "3.0.0")
        assertFalse(texto.contains("execucao"))
        assertFalse(texto.contains("somAntes"))
    }
}
