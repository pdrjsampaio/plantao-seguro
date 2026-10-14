package com.plantaoseguro

import android.content.Context
import com.plantaoseguro.agenda.Agendador
import com.plantaoseguro.agenda.Coordenador
import com.plantaoseguro.aviso.Notificador
import com.plantaoseguro.dados.Historico
import com.plantaoseguro.dados.Repositorio
import com.plantaoseguro.nucleo.CentralDeAlertas
import com.plantaoseguro.sirene.EfeitosAndroid
import com.plantaoseguro.sirene.Sirene
import com.plantaoseguro.som.ControleSom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * As peças do app, criadas uma vez só quando o processo sobe (injeção de
 * dependências manual: são poucos objetos e todos vivem o processo inteiro).
 */
class Grafo(context: Context) {
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repo = Repositorio(context)
    val historico = Historico(context) { repo.dados.value.ajustes.diasHistorico }
    val notificador = Notificador(context)
    val controleSom = ControleSom(context)
    val sirene = Sirene(context)
    val agendador = Agendador(context)
    val central = CentralDeAlertas(EfeitosAndroid(sirene, notificador, historico, agendador))
    val coordenador = Coordenador(context, repo, historico, controleSom, central, notificador, agendador)

    init {
        sirene.aoTerminarSozinho = { central.tempoEsgotado() }
    }
}
