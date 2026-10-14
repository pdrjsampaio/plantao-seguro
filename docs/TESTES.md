# Testes da versão 3.0.0-beta.1

Este documento separa três coisas: o que foi **executado no computador**
(JVM), o que foi **executado num emulador Android** com notificações de
verdade, e o que **ainda não foi validado num celular de verdade**.

Os resultados de cada compilação ficam nos branches `ci-resultados`
(compilação, testes na JVM, fotos das telas, lint) e `ci-e2e` (cenários no
emulador, com relatório e fotos tiradas no emulador).

## Resumo

| Onde | O que roda | Resultado |
|---|---|---|
| JVM | 104 testes: decisão (Motor), central de alertas, agenda, textos, formatos, dados e compatibilidade, importação da 2.5.0, arquivo de configurações, histórico, fluxos de tela com o app de verdade e 26 fotos de telas | ver `ci-resultados` |
| Emulador Android 14 (API 34, Pixel 6) | 21 cenários de ponta a ponta com UiAutomator; o app `:simulador` faz o papel do WhatsApp | **21 de 21 passaram** |
| Celular de verdade | — | **não validado ainda** (roteiro no fim deste documento) |

## Os 15 cenários pedidos

| # | Cenário | JVM | Emulador (Android 14) | Celular de verdade |
|---|---|---|---|---|
| 1 | Notificação relevante dispara o alerta | `MotorTest`, `CentralDeAlertasTest` | c01: passou | pendente |
| 2 | Notificação irrelevante não dispara | `MotorTest` (app não vigiado, resumo de grupo, em andamento, antiga, antes do plantão, fora do plantão, filtros) | c02, c19, c20: passaram | pendente |
| 3 | Duplicadas não duplicam | `MotorTest` (mesma chave e horário, `ONLY_ALERT_ONCE`) | c03: passou | pendente |
| 4 | Tocar na notificação para o alerta | `CentralDeAlertasTest` | c04 (tocar na notificação original, `REASON_CLICK`), c23 ("Abrir conversa"): passaram | pendente **com o WhatsApp real** |
| 5 | Parar manualmente | `CentralDeAlertasTest` | c05 (botão Parar da notificação), c07 (botão da tela cheia), c07b (botão de volume): passaram | pendente |
| 6 | Várias mensagens seguidas | `CentralDeAlertasTest` | c06: passou (um alerta, contador 3) | pendente |
| 7 | Celular bloqueado | — | c07, c22: tela do alerta abriu por cima do bloqueio e acendeu a tela | pendente (cada fabricante trata a tela cheia de um jeito) |
| 8 | App em segundo plano | — | todos os cenários rodam com o app fora da tela | pendente |
| 9 | Escuta interrompida | — | c09: acesso retirado → diagnóstico com erro e aviso; acesso devolvido → volta a tocar | **pendente**: fechamento pelo sistema (economia de bateria) só acontece em aparelho real |
| 10 | Reinício | `AgendaTest` (cálculo depois de qualquer pausa) | c10 (simulado): alarmes apagados → o receptor do sistema reagendou o início | **pendente**: reinício físico |
| 11 | Perda de permissão | — | c09 (acesso às notificações) | pendente: retirar "Mostrar notificações" fecha o processo, então não roda dentro do teste |
| 12 | Troca do som da regra | `FluxosTest` (escolha na biblioteca) | c12: passou | pendente (ouvir o som) |
| 13 | Áudio importado | — | c13: arquivo (`content://`) tocou; arquivo apagado → tocou o som padrão | pendente: o seletor de arquivos do Android |
| 14 | Silencioso e Não Perturbe | — | c14: silencioso + Não Perturbe → plantão tirou os dois → no fim voltou como estava | pendente |
| 15 | Atualizar sem perder dados | `DadosCompatibilidadeTest`, `Migracao25Test` | — | não se aplica à v1.0.0 (outra assinatura: precisa desinstalar) |

## Outros comportamentos verificados no emulador

| Cenário | Resultado |
|---|---|
| c16: alerta sem confirmação toca de novo depois de 1 min (AlarmManager) e para quando a mensagem é aberta | passou |
| c17: chamada (categoria `call` com tela cheia) para quando o app encerra a chamada, mesmo com o ajuste de parar desligado | passou |
| c18: com "parar ao tocar na mensagem" desligado, fechar a mensagem não para o alerta | passou |
| c21: teste de ponta a ponta do diagnóstico (a escuta percebeu em cerca de 200 ms) | passou |
| No emulador, a permissão de tela cheia veio liberada para um app instalado fora da loja | observado |

## O que o emulador não prova

- **O WhatsApp de verdade.** O simulador publica notificações no formato de
  mensagem do Android (`MessagingStyle`). O WhatsApp pode atualizar,
  agrupar ou fechar as notificações de outro jeito (por exemplo, fechar a
  notificação do celular quando a conversa é lida no computador).
- **Economia de bateria dos fabricantes** (Xiaomi, POCO, Samsung, Motorola…),
  que pode fechar o app ou atrasar alarmes.
- **Som e vibração reais:** o emulador roda sem alto-falante e sem motor de
  vibração. O teste confirma que o reprodutor começou a tocar, não que se ouve.
- **Outras versões do Android:** o app instala a partir do Android 8, mas só o
  Android 14 foi testado no emulador.
- **Reinício físico** do aparelho e troca de fuso horário.

## Roteiro para validar num celular

1. Desinstale a v1.0.0 (se tiver) e instale a 3.0.0-beta.1.
2. Siga os primeiros passos e libere tudo que o app pedir, inclusive a bateria
   sem restrição e as dicas do seu fabricante.
3. Em Ajustes > Diagnóstico, toque em **Testar a escuta** e em **Testar
   bloqueado** (bloqueie o celular e espere 10 segundos).
4. Toque em **Começar agora** na tela inicial e peça para alguém mandar uma
   mensagem no WhatsApp Business. O alerta deve tocar.
5. Toque na notificação do WhatsApp: o alerta deve parar e a conversa abrir.
6. Peça outra mensagem e deixe tocar até o fim sem mexer: o alerta deve ficar
   marcado como "sem confirmação".
7. Com o celular no silencioso e o Não Perturbe ligado, comece um plantão:
   os dois devem desligar; ao encerrar, devem voltar.
8. Deixe o celular parado com a tela apagada por pelo menos 1 hora durante um
   plantão e peça uma mensagem: é o teste mais importante para a economia de
   bateria do fabricante.
9. Reinicie o celular durante um plantão e peça uma mensagem.

## Como repetir os testes

```bash
./gradlew :app:testDebugUnitTest                                  # JVM
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true     # + fotos em app/build/outputs/roborazzi
./gradlew :simulador:assembleDebug
adb install -r simulador/build/outputs/apk/debug/simulador-debug.apk
./gradlew :app:connectedDebugAndroidTest                          # emulador ou celular de teste
```

O relatório do emulador é gravado em
`/sdcard/Android/data/com.plantaoseguro.debug/files/e2e/relatorio.md`.
