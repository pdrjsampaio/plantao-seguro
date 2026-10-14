package com.plantaoseguro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantaoseguro.Grafo

/** O grafo de dependências disponível para as telas. */
val LocalGrafo = staticCompositionLocalOf<Grafo> { error("Grafo não fornecido") }

/** Cria (ou recupera) um ViewModel que recebe o grafo. */
@Composable
inline fun <reified VM : ViewModel> vm(chave: String? = null, crossinline criar: (Grafo) -> VM): VM {
    val grafo = LocalGrafo.current
    return viewModel(key = chave, factory = viewModelFactory { initializer { criar(grafo) } })
}
