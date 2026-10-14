package com.plantaoseguro.dados

import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.modelo.Dados
import com.plantaoseguro.modelo.Plantao
import com.plantaoseguro.modelo.Regra
import com.plantaoseguro.modelo.SomRef
import kotlinx.serialization.Serializable

/**
 * Cópia das configurações para guardar ou levar para outro celular.
 * Não leva o histórico nem o estado de execução. Sons escolhidos de arquivos
 * do celular voltam para o som padrão, porque a permissão de leitura não
 * passa de um aparelho para outro.
 */
@Serializable
data class CopiaConfiguracoes(
    val formato: String = FORMATO,
    val versaoApp: String = "",
    val criadaEm: Long = 0L,
    val regras: List<Regra> = emptyList(),
    val plantoes: List<Plantao> = emptyList(),
    val ajustes: Ajustes = Ajustes()
)

const val FORMATO = "plantao-seguro/configuracoes"

object Backup {

    fun exportar(d: Dados, versaoApp: String, agora: Long = System.currentTimeMillis()): String =
        Repositorio.JSON.encodeToString(
            CopiaConfiguracoes.serializer(),
            CopiaConfiguracoes(
                versaoApp = versaoApp,
                criadaEm = agora,
                regras = d.regras.map { it.copy(silenciadaAte = 0L) },
                plantoes = d.plantoes.filterNot { it.rapido },
                ajustes = d.ajustes
            )
        )

    sealed interface Resultado {
        data class Ok(val regras: Int, val plantoes: Int, val sonsTrocados: Int) : Resultado
        data class Erro(val motivo: String) : Resultado
    }

    /** Lê uma cópia e devolve os dados que a substituem, ou o motivo de não dar. */
    fun importar(texto: String, atual: Dados): Pair<Dados?, Resultado> {
        val copia = try {
            Repositorio.JSON.decodeFromString(CopiaConfiguracoes.serializer(), texto)
        } catch (e: Exception) {
            return null to Resultado.Erro("O arquivo não é uma cópia de configurações do Plantão Seguro.")
        }
        if (copia.formato != FORMATO) {
            return null to Resultado.Erro("O arquivo não é uma cópia de configurações do Plantão Seguro.")
        }
        var trocados = 0
        val regras = copia.regras.map { r ->
            if (r.toque.som is SomRef.Arquivo) {
                trocados++
                r.copy(toque = r.toque.copy(som = SomRef.Embutido("sirene_classica")))
            } else r
        }
        val novo = Repositorio.corrigir(
            atual.copy(
                regras = regras,
                plantoes = copia.plantoes,
                ajustes = copia.ajustes.copy(boasVindasConcluida = true),
                execucao = atual.execucao.copy(pulos = emptyList())
            )
        )
        return novo to Resultado.Ok(regras.size, copia.plantoes.size, trocados)
    }
}
