package com.plantaoseguro.ui.ajuda

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plantaoseguro.ui.comum.BarraTopo

enum class TipoAjuda { AJUDA, PRIVACIDADE }

private data class Topico(val pergunta: String, val resposta: String)

private val AJUDA = listOf(
    Topico(
        "Como o Plantão Seguro funciona?",
        "Você cadastra quando está de plantão e escolhe, nas regras de alerta, quais apps (por padrão o WhatsApp Business) disparam o alerta. " +
            "Durante o plantão, quando um desses apps mostra uma notificação, o Plantão Seguro toca o som escolhido no volume de alarme, " +
            "que funciona mesmo no silencioso. Fora do plantão ele não faz nada e os apps tocam o som normal deles."
    ),
    Topico(
        "Como faço o alerta parar?",
        "Toque em Parar na tela do alerta ou na notificação, aperte um botão de volume, ou abra a mensagem: tocar ou dispensar a " +
            "notificação original também para o alerta (dá para desligar isso em Ajustes). Parar conta como \"vi o alerta\": aquela " +
            "mensagem não toca de novo."
    ),
    Topico(
        "Por que o alerta não tocou?",
        "As causas mais comuns, em ordem: o acesso às notificações foi desligado; o celular fechou o app para economizar bateria " +
            "(muito comum em Xiaomi, POCO e Samsung); o plantão não estava valendo naquele horário; a regra estava silenciada ou desligada; " +
            "o Não Perturbe estava em silêncio total. Abra Ajustes > Diagnóstico e faça o teste de ponta a ponta."
    ),
    Topico(
        "O app sabe se eu li a mensagem?",
        "Não. O Android só informa que a notificação original foi tocada, dispensada ou fechada pelo próprio app (o que costuma " +
            "acontecer quando você abre a conversa, no celular ou no computador). É isso que o Plantão Seguro usa para parar o alerta."
    ),
    Topico(
        "Como funcionam os filtros por grupo ou palavra?",
        "Numa regra, você pode limitar o alerta a certos contatos ou grupos, exigir palavras (como \"urgente\") ou ignorar palavras " +
            "(como \"bom dia\"). O app compara com o que o WhatsApp mostra na notificação, sem acentos e sem diferenciar maiúsculas. " +
            "Se você esconde o conteúdo das notificações no WhatsApp, os filtros não têm o que comparar."
    ),
    Topico(
        "E se ninguém parar o alerta?",
        "Ele toca pelo tempo escolhido na regra e para sozinho, para não gastar a bateria. O alerta fica marcado como \"sem confirmação\" " +
            "e, se a regra tiver \"tocar de novo\", volta a tocar a cada X minutos enquanto a mensagem continuar sem ser aberta."
    ),
    Topico(
        "O que é a folga?",
        "No menu de um plantão semanal, \"Folga na próxima vez\" pula só a próxima ocorrência. Encerrar um plantão que está valendo também " +
            "conta como folga daquela vez; na semana seguinte ele volta normalmente."
    ),
    Topico(
        "O que acontece com o som quando o plantão acaba?",
        "Cada plantão escolhe: voltar o som como estava antes (se o celular estava no silencioso, volta ao silencioso) ou deixar como estiver."
    ),
    Topico(
        "Limites que nenhum app consegue contornar",
        "Alguns fabricantes encerram apps mesmo com todas as permissões; o Plantão Seguro detecta e avisa, mas não consegue impedir. " +
            "No Não Perturbe em silêncio total, nem alarmes tocam, por isso o plantão pode desligá-lo. Teste no seu celular antes de confiar num plantão real."
    ),
    Topico(
        "Como atualizar ou compartilhar?",
        "As versões ficam em github.com/pdrjsampaio/plantao-seguro/releases. Antes de trocar de celular ou reinstalar, guarde uma cópia das " +
            "configurações em Ajustes."
    )
)

private val PRIVACIDADE = listOf(
    Topico(
        "O que o app vê",
        "Com o acesso às notificações, o Android entrega ao app as notificações do celular. O Plantão Seguro descarta na hora as de apps " +
            "que nenhuma regra vigia. Dos apps vigiados ele usa o nome do app, o horário e marcadores técnicos (se é chamada, se é atualização)."
    ),
    Topico(
        "Quando ele lê o texto",
        "Só se uma regra daquele app tiver filtro de contato, grupo ou palavra. Nesse caso compara o título e o texto na memória, " +
            "naquele instante, e descarta. O texto nunca é gravado, nunca entra no histórico e nunca sai do celular."
    ),
    Topico(
        "O que fica guardado",
        "Suas regras, plantões e ajustes, e um histórico de fatos (\"alerta tocou às 03:12 no WhatsApp Business, parado ao tocar na mensagem\"), " +
            "pelo número de dias que você escolher. O nome de um grupo só aparece na tela do alerta, e só com o celular desbloqueado."
    ),
    Topico(
        "O que ele nunca faz",
        "Não tem permissão de internet, então não consegue enviar nada para lugar nenhum. Não tem conta, anúncios nem rastreamento. " +
            "Não mexe no WhatsApp nem em outros apps."
    ),
    Topico(
        "Seu controle",
        "Você pode apagar o histórico a qualquer momento, desligar o acesso às notificações nas configurações do Android e exportar " +
            "suas configurações para um arquivo que só você guarda."
    )
)

@Composable
fun AjudaTela(tipo: TipoAjuda, versao: String, onVoltar: () -> Unit, onPrivacidade: () -> Unit) {
    val topicos = if (tipo == TipoAjuda.AJUDA) AJUDA else PRIVACIDADE
    Scaffold(
        topBar = { BarraTopo(if (tipo == TipoAjuda.AJUDA) "Ajuda" else "Privacidade", onVoltar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(topicos) { t ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(t.pergunta, style = MaterialTheme.typography.titleMedium)
                    Text(t.resposta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    if (tipo == TipoAjuda.AJUDA) {
                        TextButton(onClick = onPrivacidade) { Text("Ler a política de privacidade") }
                    }
                    Text(
                        "Plantão Seguro $versao. Sons próprios gerados para o projeto. Fonte Barlow, licença SIL Open Font License 1.1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
