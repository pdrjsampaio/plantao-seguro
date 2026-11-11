package com.plantaoseguro.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pacote do WhatsApp Business, o app monitorado por padrão. */
const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

/** Versão do formato salvo. Campos novos sempre têm valor padrão, então versões antigas continuam legíveis. */
const val VERSAO_DADOS = 4

/** Som usado quando nenhum outro foi escolhido (ou quando o escolhido sumiu). */
const val SOM_PADRAO = "sirene_classica"

@Serializable
enum class TipoAgenda { SEMANAL, UNICO }

/** O que fazer com o som do celular quando o plantão termina. */
@Serializable
enum class AoTerminar { RESTAURAR, MANTER }

/** Qual som o alerta toca. */
@Serializable
sealed interface SomRef {
    /** Um dos sons que vêm dentro do app. */
    @Serializable
    @SerialName("embutido")
    data class Embutido(val id: String) : SomRef

    /** Um toque de alarme do próprio celular. */
    @Serializable
    @SerialName("sistema")
    data class Sistema(val uri: String, val nome: String) : SomRef

    /** Um arquivo de áudio escolhido pela pessoa. */
    @Serializable
    @SerialName("arquivo")
    data class Arquivo(val uri: String, val nome: String) : SomRef
}

@Serializable
enum class Vibracao(val rotulo: String) {
    NENHUMA("Não vibrar"),
    PULSO("Pulsos"),
    CONTINUA("Contínua"),
    BATIMENTO("Batimento"),
    RAPIDA("Rápida"),
    SOS("SOS")
}

/** Por quanto tempo o alerta toca se ninguém parar. */
@Serializable
enum class ModoDuracao { TEMPO, REPETICOES, ATE_PARAR }

/** Como o alerta toca. */
@Serializable
data class Toque(
    /** Desligado = só vibra. */
    val tocarSom: Boolean = true,
    val som: SomRef = SomRef.Embutido(SOM_PADRAO),
    /** Volume do alarme durante o alerta, de 10 a 100 (%). */
    val volume: Int = 100,
    val crescente: Boolean = false,
    val vibracao: Vibracao = Vibracao.PULSO,
    val lanterna: Boolean = false,
    val modoDuracao: ModoDuracao = ModoDuracao.TEMPO,
    val duracaoSeg: Int = 60,
    val repeticoes: Int = 3,
    /** Se o alerta não for confirmado, toca de novo a cada X minutos enquanto a mensagem continuar lá (0 = não). */
    val insistirMin: Int = 0
)

@Serializable
enum class QuandoVale { NO_PLANTAO, SEMPRE }

/**
 * Uma regra diz o que dispara o alerta e como ele toca.
 *
 * Contatos, palavras e exceções são opcionais. Só quando existe algum deles o
 * app lê o nome da conversa e o texto da notificação — e só na memória, para
 * comparar. Nada disso é gravado.
 */
@Serializable
data class Regra(
    val id: String,
    val nome: String,
    val ligada: Boolean = true,
    val apps: Set<String> = setOf(PKG_WHATSAPP_BUSINESS),
    /** Nomes de contatos ou grupos (basta conter o trecho). */
    val contatos: List<String> = emptyList(),
    /** Palavras que precisam aparecer no título ou no texto. */
    val palavras: List<String> = emptyList(),
    /** Se alguma destas aparecer, a regra não toca. */
    val ignorar: List<String> = emptyList(),
    val quando: QuandoVale = QuandoVale.NO_PLANTAO,
    /** Só nestes plantões (vazio = em qualquer plantão). */
    val plantoes: Set<String> = emptySet(),
    val chamadas: Boolean = true,
    /**
     * Prioridade máxima: é avaliada antes das outras e toca mesmo se você
     * estiver numa ligação.
     */
    val critica: Boolean = false,
    /** Som próprio desta regra; só vale quando [somProprio] está ligado. */
    val toque: Toque = Toque(),
    /** Desligado: a regra toca o som padrão do app (Ajustes > som padrão). */
    val somProprio: Boolean = false,
    /** Silenciada temporariamente até este instante. */
    val silenciadaAte: Long = 0L,
    val criadaEm: Long = 0L
) {
    val usaConteudo: Boolean get() = contatos.isNotEmpty() || palavras.isNotEmpty() || ignorar.isNotEmpty()

    fun silenciada(agora: Long): Boolean = silenciadaAte > agora

    /** Como esta regra toca: o som próprio ou o padrão do app. */
    fun toqueEfetivo(padrao: Toque): Toque = if (somProprio) toque else padrao

    companion object {
        const val ID_PADRAO = "padrao"

        fun padrao(): Regra = Regra(id = ID_PADRAO, nome = "Mensagens de trabalho")
    }
}

/**
 * Um plantão: quando a proteção vale. Pode repetir toda semana (dias +
 * horários) ou acontecer uma vez só (início e fim exatos).
 *
 * Horários semanais são minutos desde a meia-noite. Se o fim for menor ou
 * igual ao início, o plantão termina no dia seguinte.
 */
@Serializable
data class Plantao(
    val id: String,
    val nome: String,
    val cor: Int = 0,
    val tipo: TipoAgenda = TipoAgenda.SEMANAL,
    /** Dias da semana no padrão ISO: 1 = segunda ... 7 = domingo. */
    val dias: Set<Int> = setOf(1, 2, 3, 4, 5),
    val inicioMin: Int = 19 * 60,
    val fimMin: Int = 7 * 60,
    val inicioMs: Long = 0L,
    val fimMs: Long = 0L,
    val ligado: Boolean = true,
    /** Os alertas das regras "no plantão" valem durante este plantão. */
    val tocarSirene: Boolean = true,
    val tirarDoSilencioso: Boolean = true,
    val desligarNaoPerturbe: Boolean = true,
    /** Se você baixar o volume durante o plantão, o app pergunta por quanto tempo manter. */
    val vigiarVolume: Boolean = false,
    /** Volume mínimo de toque e notificação vigiado (%). */
    val volumeMinimo: Int = 70,
    val aoTerminar: AoTerminar = AoTerminar.RESTAURAR,
    /** Avisa na entrada e na saída para bater o ponto, com atalho para o app do ponto. */
    val lembrarPonto: Boolean = false,
    /** Minutos antes do início para o aviso de entrada (0 = na hora). */
    val pontoAntesMin: Int = 10,
    /** Também avisa na hora de terminar. */
    val pontoSaida: Boolean = true,
    /** Criado pelo botão "Começar agora". */
    val rapido: Boolean = false,
    val criadoEm: Long = 0L
)

@Serializable
data class Ajustes(
    /** Tocar ou dispensar a notificação original confirma o alerta. */
    val pararAoTocarNaMensagem: Boolean = true,
    val volumeParaSirene: Boolean = true,
    val silenciarEmLigacao: Boolean = true,
    /** Mensagens com horário anterior ao início do plantão não disparam. */
    val ignorarAnteriores: Boolean = true,
    /** Duração do "Começar agora". */
    val duracaoRapidoMin: Int = 12 * 60,
    val diasHistorico: Int = 30,
    val boasVindasConcluida: Boolean = false,
    /** Pacote do app usado para bater o ponto (abre pelo lembrete e pela tela inicial). */
    val appPonto: String? = null,
    /** Som padrão do app: vale para toda regra que não tem som próprio. */
    val toquePadrao: Toque = Toque()
)

/** Uma ocorrência de plantão semanal encerrada antes da hora (ou folga). */
@Serializable
data class Pulo(val plantaoId: String, val inicioOcorrencia: Long)

/** Como o som do celular estava antes do plantão começar. */
@Serializable
data class SomSalvo(
    val modoCampainha: Int,
    val filtroInterrupcao: Int,
    val volToque: Int,
    val volNotificacao: Int,
    val salvoEm: Long
)

/** Estado de execução, para sobreviver a reinícios do celular e do app. */
@Serializable
data class Execucao(
    val pulos: List<Pulo> = emptyList(),
    val ocorrenciasAplicadas: List<String> = emptyList(),
    val somAntes: SomSalvo? = null,
    val restaurarAoFim: Boolean = true,
    /** Volume reduzido de propósito até este instante (vigia de volume). */
    val volumeReduzidoAte: Long = 0L,
    val migracao25Feita: Boolean = false,
    /** Lembretes de ponto já mostrados (para não repetir). */
    val pontoAvisados: List<String> = emptyList()
)

@Serializable
data class Dados(
    val versao: Int = VERSAO_DADOS,
    val regras: List<Regra> = listOf(Regra.padrao()),
    val plantoes: List<Plantao> = emptyList(),
    val ajustes: Ajustes = Ajustes(),
    val execucao: Execucao = Execucao()
)

@Serializable
enum class TipoEvento { ALERTA, ALERTA_CONFIRMADO, ALERTA_NAO_CONFIRMADO, PLANTAO_INICIO, PLANTAO_FIM, TESTE, PROBLEMA, SISTEMA, PONTO }

@Serializable
enum class Nivel { OK, INFO, AVISO, ERRO }

/** Registro do histórico. Nunca guarda o texto, o remetente ou o grupo das mensagens. */
@Serializable
data class Evento(
    val t: Long,
    val tipo: TipoEvento,
    val nivel: Nivel,
    val titulo: String,
    val detalhe: String? = null
)
