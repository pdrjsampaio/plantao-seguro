package com.plantaoseguro.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plantaoseguro.Grafo
import com.plantaoseguro.modelo.SomRef
import com.plantaoseguro.modelo.TipoAgenda
import com.plantaoseguro.ui.tema.PlantaoTema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fluxos completos da interface com o app de verdade (grafo, navegação,
 * ViewModels e gravação), rodando na JVM com Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h2000dp-xxhdpi")
class FluxosTest {

    @get:Rule
    val compose = createComposeRule()

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private lateinit var grafo: Grafo

    @Before
    fun preparar() {
        contexto.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        grafo = Grafo(contexto)
        MainActivity.destinoPendente = null
    }

    private fun abrirApp() {
        compose.setContent {
            PlantaoTema {
                CompositionLocalProvider(LocalGrafo provides grafo) {
                    AppPlantao(destino = MainActivity.destinoPendente, onDestinoAberto = { MainActivity.destinoPendente = null })
                }
            }
        }
    }

    private fun esperarTexto(texto: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            // Mostra o que estava na tela, para entender a falha pelo relatório.
            throw AssertionError("Não apareceu \"$texto\". Tela:\n" + compose.onRoot().printToString(maxDepth = 40), e)
        }
    }

    @Test
    fun primeiros_passos_levam_ao_cadastro_do_plantao() {
        abrirApp()
        compose.onNodeWithText("Começar").performClick()
        repeat(3) { compose.onNodeWithText("Continuar").performClick() }
        compose.onNodeWithText("Cadastrar meu plantão").performClick()

        // "Salvar plantão" só existe no editor (o botão da tela inicial também se chama "Novo plantão").
        esperarTexto("Salvar plantão")
        compose.onNodeWithText("Plantão noturno").performClick()
        compose.onNodeWithText("Salvar plantão").performClick()

        compose.waitUntil(10_000) { grafo.repo.dados.value.plantoes.any { it.nome == "Plantão noturno" } }
        assertTrue(grafo.repo.dados.value.ajustes.boasVindasConcluida)
        esperarTexto("Seus plantões")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Segunda a sexta"))
        compose.onAllNodesWithText("Plantão noturno").onFirst().assertExists()
    }

    @Test
    fun comecar_agora_nos_primeiros_passos_liga_o_plantao() {
        abrirApp()
        compose.onNodeWithText("Começar").performClick()
        repeat(3) { compose.onNodeWithText("Continuar").performClick() }
        compose.onNodeWithText("Começar um plantão agora").performClick()
        compose.waitUntil(10_000) { grafo.coordenador.ativasAgora().isNotEmpty() }
        esperarTexto("Em plantão")
    }

    @Test
    fun plantao_de_uma_vez_com_calendario_e_relogio() {
        runBlocking { grafo.repo.atualizar { it.copy(ajustes = it.ajustes.copy(boasVindasConcluida = true)) } }
        MainActivity.destinoPendente = "plantao/novo"
        abrirApp()
        esperarTexto("Salvar plantão")
        compose.onNodeWithText("Uma vez").performClick()
        compose.onAllNodesWithText("Dia")[0].performClick()
        esperarTexto("OK")
        compose.onNodeWithText("OK").performClick()
        compose.onAllNodesWithText("Hora")[1].performClick()
        esperarTexto("Termina às")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Salvar plantão").performClick()
        compose.waitUntil(10_000) { grafo.repo.dados.value.plantoes.any { it.tipo == TipoAgenda.UNICO } }
    }

    @Test
    fun nova_regra_com_som_escolhido_na_biblioteca() {
        runBlocking { grafo.repo.atualizar { it.copy(ajustes = it.ajustes.copy(boasVindasConcluida = true)) } }
        abrirApp()
        compose.onNodeWithText("Alertas").performClick()
        // O Material 3 tira o texto do botão flutuante da acessibilidade; o app põe a descrição.
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Nova regra").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Nova regra").performClick()

        esperarTexto("Nome da regra")
        compose.onNodeWithText("Nome da regra").performTextInput("Chefia")
        compose.onNodeWithText("Escolher som").performClick()

        esperarTexto("Som do alerta")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sirene rápida"))
        compose.onNodeWithText("Sirene rápida").performClick()
        compose.onNodeWithText("Usar Sirene rápida").performClick()

        // De volta ao editor, com o som escolhido.
        esperarTexto("Sirene rápida, volume 100%")
        compose.onNodeWithText("Salvar regra").performClick()

        compose.waitUntil(10_000) { grafo.repo.dados.value.regras.any { it.nome == "Chefia" } }
        val regra = grafo.repo.dados.value.regras.first { it.nome == "Chefia" }
        assertEquals(SomRef.Embutido("sirene_rapida"), regra.toque.som)
        esperarTexto("Chefia")
    }
}
