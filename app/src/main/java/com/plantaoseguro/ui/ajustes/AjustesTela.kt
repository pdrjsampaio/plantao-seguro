package com.plantaoseguro.ui.ajustes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plantaoseguro.modelo.Ajustes
import com.plantaoseguro.ui.comum.Bloco
import com.plantaoseguro.ui.comum.Divisoria
import com.plantaoseguro.ui.comum.Fichas
import com.plantaoseguro.ui.comum.LinhaAjuste
import com.plantaoseguro.ui.comum.LinhaComConteudo
import com.plantaoseguro.ui.comum.LinhaInterruptor
import com.plantaoseguro.ui.comum.TituloSecao
import com.plantaoseguro.ui.tema.Extras
import com.plantaoseguro.util.Formato

class AcoesAjustes(
    val mudar: ((Ajustes) -> Ajustes) -> Unit = {},
    val permissoes: () -> Unit = {},
    val diagnostico: () -> Unit = {},
    val testar: () -> Unit = {},
    val exportar: () -> Unit = {},
    val importar: () -> Unit = {},
    val ajuda: () -> Unit = {},
    val privacidade: () -> Unit = {},
    val compartilhar: () -> Unit = {}
)

@Composable
fun AjustesTela(a: Ajustes, pendencias: Int, versao: String, acoes: AcoesAjustes) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)
            )
        }

        item {
            TituloSecao("Proteção")
            Bloco {
                LinhaAjuste(
                    Icons.Rounded.Shield,
                    "Permissões e bateria",
                    if (pendencias == 0) "Tudo liberado." else "Falta liberar $pendencias. Sem isso o alerta pode não tocar.",
                    corIcone = if (pendencias == 0) Extras.cores.ok else Extras.cores.aviso,
                    onClick = acoes.permissoes
                )
                Divisoria()
                LinhaAjuste(
                    Icons.Rounded.MedicalServices,
                    "Diagnóstico",
                    "Confere item por item se o monitoramento está funcionando.",
                    onClick = acoes.diagnostico
                )
                Divisoria()
                LinhaAjuste(
                    Icons.Rounded.NotificationsActive,
                    "Testar o alerta",
                    "Toca o alerta da primeira regra ligada.",
                    corIcone = Extras.cores.sinal,
                    onClick = acoes.testar
                )
            }
        }

        item {
            TituloSecao("Como o alerta para")
            Bloco {
                LinhaInterruptor(
                    Icons.Rounded.TouchApp,
                    "Parar ao tocar na mensagem",
                    "Tocar, abrir ou dispensar a notificação original silencia o alerta.",
                    a.pararAoTocarNaMensagem,
                    { v -> acoes.mudar { it.copy(pararAoTocarNaMensagem = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.AutoMirrored.Rounded.VolumeDown,
                    "Botões de volume param o alerta",
                    "Na tela cheia do alerta.",
                    a.volumeParaSirene,
                    { v -> acoes.mudar { it.copy(volumeParaSirene = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.Rounded.PhoneInTalk,
                    "Não tocar se eu estiver numa ligação",
                    "Nesse caso o celular só vibra. Regras de prioridade máxima tocam mesmo assim.",
                    a.silenciarEmLigacao,
                    { v -> acoes.mudar { it.copy(silenciarEmLigacao = v) } }
                )
                Divisoria()
                LinhaInterruptor(
                    Icons.Rounded.Schedule,
                    "Ignorar mensagens de antes do plantão",
                    "Mensagens que chegaram antes do início não disparam quando reaparecem.",
                    a.ignorarAnteriores,
                    { v -> acoes.mudar { it.copy(ignorarAnteriores = v) } }
                )
            }
        }

        item {
            TituloSecao("Começar agora")
            Bloco {
                LinhaComConteudo(Icons.Rounded.Bolt, "Duração padrão", "Do botão Começar agora e do atalho nas configurações rápidas.") {
                    Fichas(
                        listOf(120, 240, 480, 720, 1440).map { it to Formato.duracao(it * 60_000L) },
                        a.duracaoRapidoMin,
                        { v -> acoes.mudar { it.copy(duracaoRapidoMin = v) } }
                    )
                }
            }
        }

        item {
            TituloSecao("Seus dados")
            Bloco {
                LinhaAjuste(
                    Icons.Rounded.FileDownload,
                    "Guardar uma cópia das configurações",
                    "Regras, plantões e ajustes num arquivo, para restaurar depois ou usar em outro celular.",
                    onClick = acoes.exportar
                )
                Divisoria()
                LinhaAjuste(
                    Icons.Rounded.FileUpload,
                    "Restaurar uma cópia",
                    "Substitui as regras e plantões atuais pelos do arquivo.",
                    onClick = acoes.importar
                )
                Divisoria()
                LinhaComConteudo(Icons.Rounded.History, "Guardar o histórico por", null) {
                    Fichas(
                        listOf(7 to "7 dias", 30 to "30 dias", 90 to "90 dias"),
                        a.diasHistorico,
                        { v -> acoes.mudar { it.copy(diasHistorico = v) } }
                    )
                }
            }
        }

        item {
            TituloSecao("Ajuda e sobre")
            Bloco {
                LinhaAjuste(Icons.AutoMirrored.Rounded.HelpOutline, "Ajuda", "Como funciona, por que não tocou e limites do Android.", onClick = acoes.ajuda)
                Divisoria()
                LinhaAjuste(Icons.Rounded.PrivacyTip, "Privacidade", "O que o app vê, o que guarda e o que nunca faz.", onClick = acoes.privacidade)
                Divisoria()
                LinhaAjuste(Icons.Rounded.Share, "Compartilhar o app", "Mande o link para colegas de plantão.", onClick = acoes.compartilhar)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "Plantão Seguro $versao. Funciona só no seu celular, sem internet e sem conta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
