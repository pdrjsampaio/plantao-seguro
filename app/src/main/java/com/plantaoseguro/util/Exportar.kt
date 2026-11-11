package com.plantaoseguro.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.plantaoseguro.modelo.Evento
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Exportação do histórico e utilidades de arquivo/compartilhamento. */
object Exportar {

    const val LINK_DOWNLOAD = "https://github.com/pdrjsampaio/plantao-seguro/releases"

    private val fData = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Formato.LOCAL)

    private fun data(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(fData)

    fun historicoTexto(eventos: List<Evento>): String = buildString {
        appendLine("Plantão Seguro — histórico")
        appendLine("Gerado em ${data(System.currentTimeMillis())}. O texto das mensagens nunca é registrado.")
        appendLine()
        for (e in eventos.sortedBy { it.t }) {
            append(data(e.t)).append("  ").append(e.titulo)
            if (e.detalhe != null) append(" — ").append(e.detalhe)
            appendLine()
        }
    }

    fun historicoCsv(eventos: List<Evento>): String = buildString {
        appendLine("data_hora,tipo,nivel,titulo,detalhe")
        for (e in eventos.sortedBy { it.t }) {
            appendLine(listOf(data(e.t), e.tipo.name, e.nivel.name, e.titulo, e.detalhe ?: "").joinToString(",") { csv(it) })
        }
    }

    private fun csv(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** Grava num arquivo temporário e abre a folha de compartilhamento do Android. */
    fun compartilharArquivo(context: Context, nome: String, conteudo: String, tipo: String, titulo: String) {
        val pasta = File(context.cacheDir, "exportacoes").apply { mkdirs() }
        val arquivo = File(pasta, nome)
        arquivo.writeText(conteudo)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", arquivo)
        val envio = Intent(Intent.ACTION_SEND)
            .setType(tipo)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(envio, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun compartilharApp(context: Context) {
        val texto = "Plantão Seguro: alerta forte para mensagens de trabalho durante o plantão, mesmo no silencioso. " +
            "Baixe a última versão em $LINK_DOWNLOAD"
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        context.startActivity(Intent.createChooser(envio, "Compartilhar o Plantão Seguro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun nomeDoArquivo(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.') ?: "Som escolhido"

    fun lerTexto(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    fun escreverTexto(context: Context, uri: Uri, texto: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(texto.encodeToByteArray()) } != null
    }.getOrDefault(false)
}
