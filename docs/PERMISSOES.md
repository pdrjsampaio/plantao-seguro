# Permissões do Plantão Seguro

O app pede só o necessário para o alerta tocar com o celular no silencioso,
na madrugada e com a tela bloqueada. **Não pede** internet, contatos,
localização, microfone, câmera nem acesso aos seus arquivos.

Nos primeiros passos e em **Ajustes > Permissões** cada item tem um botão
"Permitir" que abre a tela certa do Android, e **Ajustes > Diagnóstico**
mostra o que está faltando.

## Liberadas por você (telas especiais do Android)

| Permissão (como aparece no app) | Nome técnico | Para que serve | Sem ela |
|---|---|---|---|
| Acesso às notificações | `BIND_NOTIFICATION_LISTENER_SERVICE` | Perceber que chegou notificação nos apps escolhidos e saber quando a notificação original foi tocada ou fechada (para parar o alerta). Veja o que é lido em [PRIVACIDADE.md](PRIVACIDADE.md). | O alerta nunca toca. |
| Mostrar notificações (Android 13+) | `POST_NOTIFICATIONS` | Mostrar o alerta com o botão Parar, o aviso de plantão em andamento e os avisos de problema. | O som toca, mas sem a notificação com o botão Parar e sem a tela cheia: para parar, é preciso abrir o app. |
| Abrir com a tela bloqueada (Android 14+) | `USE_FULL_SCREEN_INTENT` | Acender a tela e mostrar a tela grande do alerta por cima do bloqueio. | O alerta toca, mas aparece só como notificação. |
| Controlar o Não Perturbe | `ACCESS_NOTIFICATION_POLICY` | Tirar o celular do silencioso e do Não Perturbe quando o plantão começa e devolver como estava no fim. | O plantão não mexe no modo de som. O alerta ainda toca no silencioso, mas não no "silêncio total" do Não Perturbe. |
| Bateria sem restrição | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Pedir ao Android que não feche o app durante a madrugada. | Em vários aparelhos (Xiaomi, POCO, Samsung, Motorola…) o sistema pode fechar o app e o alerta não toca. |
| Alarmes no horário exato (Android 12) | `SCHEDULE_EXACT_ALARM` | Começar e terminar o plantão no minuto certo. A partir do Android 13 é usada `USE_EXACT_ALARM`, que o Android concede na instalação. | O plantão pode começar ou terminar alguns minutos atrasado. |

## Concedidas na instalação

| Nome técnico | Para que serve |
|---|---|
| `USE_EXACT_ALARM` (Android 13+) | Começo e fim do plantão e o "tocar de novo" de alertas sem confirmação no horário exato. |
| `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE` | Durante o plantão, mantém a notificação discreta "Em plantão", o que reduz a chance de o sistema fechar o app. Fora do plantão o serviço não roda. |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar os plantões quando o celular liga. |
| `WAKE_LOCK` | Manter o processador acordado enquanto o alerta toca (no máximo o tempo do alerta). |
| `VIBRATE` | Padrões de vibração do alerta. |

A lanterna (opção "piscar a lanterna") usa o controle de lanterna do Android,
que não exige a permissão de câmera.

## Apps visíveis para o Plantão Seguro

A partir do Android 11, um app só enxerga outros apps declarados. O manifesto
declara os apps de mensagem mais comuns (WhatsApp, WhatsApp Business,
Telegram, Signal, Teams, Slack, Discord, Google Chat, Mensagens do Google e da
Samsung, Gmail e Outlook) e a lista de apps com ícone na tela inicial, para
você poder escolher qualquer um. Isso serve apenas para mostrar nome e ícone.

## Como tirar uma permissão

Configurações do Android > Apps > Plantão Seguro. O acesso às notificações
fica em Configurações > Notificações > Acesso às notificações (o caminho muda
um pouco de fabricante para fabricante). Se você tirar alguma, o Diagnóstico
do app passa a mostrar o problema e, durante o plantão, o app avisa.
