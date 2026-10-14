# Plantão Seguro

Alerta forte no celular Android para mensagens de trabalho durante o plantão,
mesmo com o celular no silencioso, na madrugada e com a tela bloqueada.

Você cadastra quando está de plantão e escolhe quais apps (por padrão o
WhatsApp Business) disparam o alerta. Durante o plantão, quando chega uma
mensagem, o app toca uma sirene no volume de alarme e mostra uma tela grande
com o botão **Parar**. Tocar na mensagem (na notificação do WhatsApp) também
para o alerta. Fora do plantão ele não faz nada.

## Baixar

| Versão | Situação | Link |
|---|---|---|
| **3.0.0-beta.1** | Versão nova, em teste. Testada em computador e em emulador; falta a confirmação em celulares de verdade. | [Página da versão](https://github.com/pdrjsampaio/plantao-seguro/releases/tag/v3.0.0-beta.1) |
| 1.0.0 | Versão anterior (por dentro, 2.5.0). | [Página da versão](https://github.com/pdrjsampaio/plantao-seguro/releases/tag/v1.0.0) |

Todas as versões: https://github.com/pdrjsampaio/plantao-seguro/releases

## Instalar

1. **Quem tem a v1.0.0 instalada precisa desinstalá-la antes.** Ela foi
   assinada com uma chave de desenvolvimento diferente, e o Android não deixa
   instalar a 3.0.0 por cima. Anote seus plantões antes: eles precisam ser
   cadastrados de novo.
2. Baixe o arquivo `.apk` da página da versão, no celular.
3. Abra o arquivo. Se o Android pedir, permita instalar apps desta fonte
   (é o navegador ou o gerenciador de arquivos que você usou).
4. Abra o Plantão Seguro e siga os primeiros passos: escolher os apps,
   liberar as permissões, escolher o som e fazer um teste.
5. **Faça o teste com a tela bloqueada** (Ajustes > Diagnóstico > Testar
   bloqueado) antes de confiar num plantão de verdade.

Para conferir que o arquivo é oficial: todas as versões a partir da 3.0.0 são
assinadas com o certificado SHA-256
`F7:E8:77:22:7A:C1:5A:26:B8:8E:B9:75:F7:37:BD:BD:AB:4B:E0:66:2D:2E:FE:15:19:18:72:DC:56:63:53:73`
(veja `assinatura/LEIA.md`).

## O que ele faz

- **Plantões** semanais (inclusive os que viram a noite) ou de uma vez só,
  escala dos próximos 7 dias, folga na próxima vez, começar ou encerrar agora.
- **Regras de alerta:** quais apps, só certos contatos ou grupos, só com
  certas palavras, nunca com certas palavras, só no plantão ou sempre,
  prioridade máxima, silenciar por um tempo.
- **22 sons próprios** em 7 grupos (sirenes, alarmes intensos, estilo
  hospitalar, eletrônicos, progressivos, discretos, curtos), toques do
  celular ou um arquivo seu; volume, volume crescente, duração, repetições,
  6 padrões de vibração e lanterna.
- **Parar o alerta:** botão Parar (tela cheia ou notificação), botões de
  volume, tocar ou dispensar a notificação original, "Parar e abrir a conversa".
- **Um alarme por vez**, sem tocar de novo uma mensagem já confirmada; alerta
  sem confirmação pode tocar de novo a cada X minutos.
- **No começo do plantão** tira do silencioso e do Não Perturbe; **no fim**
  devolve como estava.
- **Diagnóstico** com teste de ponta a ponta, histórico exportável e cópia
  das configurações.

## Limites reais

Nenhum app consegue garantir funcionamento em 100% dos celulares. O que se
sabe, e o que o app faz a respeito:

- **O app não sabe se você leu a mensagem.** O Android só informa que a
  notificação original foi tocada, dispensada ou fechada pelo app de
  mensagens (por exemplo, ao abrir a conversa no celular ou no computador).
  É isso que para o alerta.
- **Economia de bateria de alguns fabricantes** (Xiaomi, POCO, Samsung,
  Motorola, entre outros) pode fechar o app mesmo com as permissões. O app
  mostra os ajustes de cada marca, verifica a escuta a cada 30 minutos
  durante o plantão e avisa quando algo parou, mas não consegue impedir.
- **"Silêncio total" do Não Perturbe** cala até alarmes. Por isso o plantão
  pode desligar o Não Perturbe (precisa dessa permissão).
- **Notificações que chegam enquanto a escuta está desligada** pelo sistema
  não tocam quando ela volta, para não disparar alertas repetidos de
  mensagens antigas.
- **Filtros por contato ou palavra** dependem do que o app de mensagens
  mostra na notificação. Se ele esconde o conteúdo, o filtro não tem o que
  comparar.

Mais detalhes em `docs/AUDITORIA.md` (seção 6) e o resultado dos testes em
`docs/TESTES.md`.

## Privacidade

Sem internet, sem conta, sem anúncios. O texto das mensagens só é conferido,
na memória, quando uma regra tem filtro, e nunca é gravado.
Política completa: [docs/PRIVACIDADE.md](docs/PRIVACIDADE.md) ·
permissões: [docs/PERMISSOES.md](docs/PERMISSOES.md).

## Para desenvolvedores

### Compilar

Requisitos: JDK 17 e Android SDK (API 35). Com o Android Studio, basta abrir a
pasta do projeto.

```bash
./gradlew :app:assembleDebug          # APK de teste (com.plantaoseguro.debug)
./gradlew :app:testDebugUnitTest      # testes na JVM
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true   # fotos das telas
./gradlew :app:connectedDebugAndroidTest   # cenários no emulador (instale antes o :simulador)
```

O APK de release só é assinado quando as variáveis `PS_KEYSTORE`,
`PS_KEYSTORE_PASSWORD`, `PS_KEY_ALIAS` e `PS_KEY_PASSWORD` existem. No GitHub
Actions elas vêm da chave criptografada no repositório, aberta com o secret
`PS_CHAVE` (`assinatura/LEIA.md`). Sem elas, sai `app-release-unsigned.apk`.

### Organização

```
app/src/main/java/com/plantaoseguro/
  modelo/      dados salvos (regras, plantões, ajustes)
  nucleo/      decisão: Motor (toca ou não), CentralDeAlertas (estado do alerta), Texto
  agenda/      Agenda (cálculo puro dos plantões), Coordenador, alarmes do sistema
  sentinela/   escuta de notificações (adaptador entre o Android e o núcleo)
  sirene/      som, vibração, lanterna, tela do alerta
  som/         silencioso, Não Perturbe e volumes
  servico/     serviço em primeiro plano durante o plantão, vigia de volume, bloco nas configurações rápidas
  dados/       arquivo de configurações, histórico, cópia, importação da 2.5.0
  sistema/     permissões, diagnóstico, apps instalados
  ui/          telas em Jetpack Compose
simulador/     app auxiliar dos testes no emulador (faz o papel do WhatsApp; não é distribuído)
ferramentas/sons/sintetizar.py   gera os 22 sons do app
```

O núcleo (`nucleo/` e `agenda/Agenda.kt`) não depende do Android e é coberto
por testes de unidade. Decisões de arquitetura e o histórico do projeto estão
em `docs/AUDITORIA.md`.

### Sons e fontes

Os sons foram sintetizados para o projeto (`ferramentas/sons/sintetizar.py`)
e podem ser redistribuídos com ele. A fonte Barlow é distribuída sob a SIL
Open Font License 1.1 (`docs/licencas/OFL-Barlow.txt`).

## Mudanças

Veja [CHANGELOG.md](CHANGELOG.md).
