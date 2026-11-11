package com.plantaoseguro.sirene

import android.content.Context
import android.media.RingtoneManager
import androidx.annotation.RawRes
import com.plantaoseguro.R
import com.plantaoseguro.modelo.SomRef

enum class CategoriaSom(val titulo: String) {
    SIRENE("Sirenes de emergência"),
    INTENSO("Alarmes intensos"),
    HOSPITALAR("Estilo hospitalar"),
    ELETRONICO("Eletrônicos"),
    PROGRESSIVO("Progressivos"),
    DISCRETO("Discretos"),
    CURTO("Notificações curtas")
}

data class SomEmbutido(
    val id: String,
    val nome: String,
    val descricao: String,
    val categoria: CategoriaSom,
    @RawRes val res: Int
)

/**
 * Sons que vêm dentro do app. Todos foram sintetizados para este projeto
 * (o gerador está em ferramentas/sons), então podem ser distribuídos sem
 * direitos de terceiros.
 */
object Catalogo {

    val sons: List<SomEmbutido> = listOf(
        SomEmbutido("sirene_classica", "Sirene clássica", "Sobe e desce devagar, como viatura", CategoriaSom.SIRENE, R.raw.sirene_classica),
        SomEmbutido("sirene_rapida", "Sirene rápida", "Vai e volta três vezes por segundo", CategoriaSom.SIRENE, R.raw.sirene_rapida),
        SomEmbutido("sirene_europeia", "Sirene de dois tons", "Alterna entre duas notas", CategoriaSom.SIRENE, R.raw.sirene_europeia),
        SomEmbutido("sirene_longa", "Sirene de alerta", "Sobe devagar, sustenta e desce", CategoriaSom.SIRENE, R.raw.sirene_longa),
        SomEmbutido("sirene_whoop", "Sirene crescente", "Repete uma subida curta", CategoriaSom.SIRENE, R.raw.sirene_whoop),
        SomEmbutido("alarme_incendio", "Alarme de incêndio", "Três toques agudos e uma pausa", CategoriaSom.INTENSO, R.raw.alarme_incendio),
        SomEmbutido("despertador_digital", "Despertador digital", "Quatro bipes rápidos", CategoriaSom.INTENSO, R.raw.despertador_digital),
        SomEmbutido("telefone_antigo", "Telefone antigo", "Campainha de sino", CategoriaSom.INTENSO, R.raw.telefone_antigo),
        SomEmbutido("chamada_enfermagem", "Chamada de enfermagem", "Dim-dom de dois tons", CategoriaSom.HOSPITALAR, R.raw.chamada_enfermagem),
        SomEmbutido("monitor_bipe", "Bipe de monitor", "Um bipe por segundo", CategoriaSom.HOSPITALAR, R.raw.monitor_bipe),
        SomEmbutido("bipe_pager", "Bipe de pager", "Três bipes subindo", CategoriaSom.HOSPITALAR, R.raw.bipe_pager),
        SomEmbutido("eletronico_arpejo", "Arpejo", "Quatro notas rápidas, duas vezes", CategoriaSom.ELETRONICO, R.raw.eletronico_arpejo),
        SomEmbutido("eletronico_laser", "Laser", "Três disparos descendo", CategoriaSom.ELETRONICO, R.raw.eletronico_laser),
        SomEmbutido("eletronico_digital", "Digital", "Sequência de bipes de computador", CategoriaSom.ELETRONICO, R.raw.eletronico_digital),
        SomEmbutido("progressivo_bipes", "Bipes que aceleram", "Começa devagar e vai ficando urgente", CategoriaSom.PROGRESSIVO, R.raw.progressivo_bipes),
        SomEmbutido("progressivo_sirene", "Sirene que acelera", "Começa calma e vai ficando mais forte", CategoriaSom.PROGRESSIVO, R.raw.progressivo_sirene),
        SomEmbutido("carrilhao", "Carrilhão", "Três notas de sino", CategoriaSom.DISCRETO, R.raw.carrilhao),
        SomEmbutido("sonar", "Sonar", "Um sinal com eco", CategoriaSom.DISCRETO, R.raw.sonar),
        SomEmbutido("gota", "Gota", "Dois pingos suaves", CategoriaSom.DISCRETO, R.raw.gota),
        SomEmbutido("curto_ping", "Ping", "Um toque curto e claro", CategoriaSom.CURTO, R.raw.curto_ping),
        SomEmbutido("curto_duplo", "Duplo", "Duas notas rápidas", CategoriaSom.CURTO, R.raw.curto_duplo),
        SomEmbutido("curto_atencao", "Atenção", "Três notas subindo", CategoriaSom.CURTO, R.raw.curto_atencao)
    )

    val padrao: SomEmbutido get() = sons.first()

    fun porId(id: String): SomEmbutido? = sons.firstOrNull { it.id == id }

    fun nome(som: SomRef): String = when (som) {
        is SomRef.Embutido -> porId(som.id)?.nome ?: padrao.nome
        is SomRef.Sistema -> som.nome
        is SomRef.Arquivo -> som.nome
    }

    /** Toques de alarme instalados no celular. Rode fora da thread principal. */
    fun alarmesDoCelular(context: Context): List<SomRef.Sistema> = try {
        val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = rm.cursor
        val lista = ArrayList<SomRef.Sistema>()
        while (cursor.moveToNext()) {
            val titulo = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
            val uri = rm.getRingtoneUri(cursor.position) ?: continue
            lista += SomRef.Sistema(uri.toString(), titulo)
        }
        lista.distinctBy { it.uri }
    } catch (e: Exception) {
        emptyList()
    }
}
