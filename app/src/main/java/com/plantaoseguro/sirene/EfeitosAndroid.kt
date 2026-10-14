package com.plantaoseguro.sirene

import com.plantaoseguro.agenda.Agendador
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.dados.Historico
import com.plantaoseguro.modelo.Nivel
import com.plantaoseguro.modelo.TipoEvento
import com.plantaoseguro.modelo.Toque
import com.plantaoseguro.nucleo.AlertaAtivo
import com.plantaoseguro.nucleo.Efeitos

/** Liga a central de alertas (Kotlin puro) às peças Android de verdade. */
class EfeitosAndroid(
    private val sirene: Sirene,
    private val notificador: Notificador,
    private val historico: Historico,
    private val agendador: Agendador
) : Efeitos {
    override fun tocar(toque: Toque, alerta: AlertaAtivo): Boolean = sirene.tocar(toque)
    override fun renovar(toque: Toque) = sirene.renovar(toque)
    override fun parar() = sirene.parar()
    override fun mostrarAlerta(alerta: AlertaAtivo) = notificador.mostrarAlerta(alerta)
    override fun limparAlerta() = notificador.limparAlerta()
    override fun mostrarNaoConfirmado(alerta: AlertaAtivo) = notificador.mostrarNaoConfirmado(alerta)
    override fun limparNaoConfirmado() = notificador.limparNaoConfirmado()
    override fun problema(titulo: String, texto: String) = notificador.problema(titulo, texto)
    override fun registrar(tipo: TipoEvento, nivel: Nivel, titulo: String, detalhe: String?) =
        historico.registrar(tipo, nivel, titulo, detalhe)
    override fun agendarInsistencia(chave: String, pacote: String, quando: Long) =
        agendador.agendarInsistencia(chave, pacote, quando)
    override fun cancelarInsistencia(chave: String) = agendador.cancelarInsistencia(chave)
}
