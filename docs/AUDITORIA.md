# Plantão Seguro — auditoria técnica e plano da versão 3

Documento das fases 1 a 4 (recuperação, diagnóstico, planejamento e redesenho).
Cada afirmação sobre a versão publicada vem da análise do APK v1.0.0; quando algo
é inferido e não observado, está marcado como **inferência**.

Data da análise: 13/10/2026.

---

## 1. O que foi recuperado

### 1.1 Fontes encontradas

| Fonte | Data | Conteúdo | Situação |
|---|---|---|---|
| Conversa "Ignorar mensagens anteriores sob demanda" | 01/07/2026 | Especificação original, documento de decisões de engenharia e a **Fase 1** (35 arquivos, troca do som do WhatsApp Business por **Acessibilidade**) | Código lido na conversa |
| Conversa "Análise de desempenho insuficiente" | 02/07/2026 | **v2 "Sentinela"**: removeu a Acessibilidade, passou a usar **acesso às notificações** e a tocar a sirene do próprio app | 21 arquivos reconstruídos a partir do histórico |
| Repositório GitHub (`main`) | 13/10/2026 | Só um `README.md` com o link do APK | Sem código-fonte |
| Release `v1.0.0` (`app-debug.apk`, 59,4 MB) | 14/10/2026 | APK compilado | Analisado por descompilação |

SHA-256 do APK analisado: `c4e9175ed844488e14ece1c22b49f5338d1f0a24e42074e23db49f0541256074`.

### 1.2 A descoberta principal

**O APK publicado não corresponde a nenhuma das duas conversas.** O manifesto diz
`versionName 2.5.0-diag-design` (com `versionCode 1`), e a estrutura é uma terceira
versão, híbrida:

- tem a automação por Acessibilidade da Fase 1 (`automation/WhatsAppAutomationService`),
  que a v2 tinha removido;
- tem uma escuta de notificações com outro nome (`notification/SireneNotificationListener`),
  diferente do `sentinela/SentinelaService` da v2;
- tem recursos que não aparecem em nenhuma conversa: vigilância do volume
  (`audio/VolumeWatcher` + `ui/VolumeChoiceActivity`), "folga hoje" do Modo Trabalho,
  modo de captura de rótulos do WhatsApp (`DiagnosticoScreen`) e WorkManager.

O certificado de assinatura foi gerado em 11/07/2026, depois das duas conversas. A
conclusão é que essa versão foi feita fora das conversas encontradas.

**O que falta:** o código-fonte original da 2.5.0. Ele não está no GitHub nem nas
conversas. A descompilação (283 classes do app, sem ofuscação por ser build de
depuração) recupera estrutura, fluxos e textos, mas não é o projeto original: perde
nomes de variáveis locais, comentários, `build.gradle`, versões de bibliotecas e
recursos de design. Por isso a v3 não "continua" um código que não existe; ela parte
do que foi observado e preserva os comportamentos que funcionam (seção 3.2).

### 1.3 Inventário da versão publicada (2.5.0, observado no APK)

| Área | Componentes | O que faz |
|---|---|---|
| Escuta | `SireneNotificationListener` | Para cada notificação de qualquer app, abre uma corrotina e consulta o banco. Se for do WhatsApp Business e houver plantão, toca o som. Registra no log também as notificações do WhatsApp pessoal. |
| Som | `SirenePlayer`, `res/raw/sirene_plantao.mp3` (17 s) | `MediaPlayer` no uso de alarme, toca uma vez (sem repetir) e para. |
| Automação | `WhatsAppAutomationService`, `RoteiroNavegacao` | Acessibilidade restrita ao `com.whatsapp.w4b`, com leitura do conteúdo da janela, para trocar o "Som de notificação" dentro do WhatsApp. Espera até 45 s pela automação. |
| Volume | `AudioController`, `VolumeWatcher`, `VolumeChoiceActivity` | No Modo Trabalho, se você baixa o volume, pergunta por quanto tempo manter (30 min, 1 h, 2 h, resto do dia) e restaura depois. |
| Agenda | `AlarmScheduler`, `AlarmReceiver`, `BootReceiver` | `setAlarmClock` para início/fim; Modo Trabalho fixo **07:50–17:00, seg–sex**; reagenda no boot. |
| Serviço | `PlantaoForegroundService` | Serviço em primeiro plano (`specialUse`) durante o plantão. |
| Dados | Room `plantao_seguro.db` v1 | Tabelas `plantoes`, `eventos_log`, `config_audio`. Usa `fallbackToDestructiveMigration()`. |
| Telas | Home, Plantões, Adicionar plantão, Configurações, Diagnóstico, Histórico | Compose + Material 3, com Hilt. |
| Permissões declaradas | 11 | Inclui `READ_MEDIA_AUDIO` e `ACCESS_NETWORK_STATE` (sem uso no código do app). Não declara `USE_FULL_SCREEN_INTENT`, `VIBRATE` nem pedido de isenção de bateria. |

---

## 2. Diagnóstico

### 2.1 O que funciona e deve ser preservado

- Tocar um som do próprio app no **canal de alarme** quando chega notificação do
  WhatsApp Business durante o plantão. É a ideia certa (veio da v2) e é o motivo de
  "funcionar" hoje.
- Agendar início e fim com `setAlarmClock` (o tipo de alarme que os fabricantes
  menos atrasam) e reagendar depois de reiniciar.
- Serviço em primeiro plano **só durante o plantão**, não o tempo todo.
- Tirar do silencioso e desligar o Não Perturbe no início, com permissão.
- Plantões com repetição semanal, Modo Trabalho em horário comercial, "folga hoje".
- A vigilância do volume no Modo Trabalho, com a pergunta "por quanto tempo".
- Histórico do que o app fez, sem conteúdo de mensagens.
- Instruções para o Autostart do HyperOS.

### 2.2 Problemas encontrados

Gravidade: **Crítica** (pode fazer perder um alerta ou expor dados), **Alta**
(comportamento errado frequente), **Média** (incômodo ou risco limitado),
**Baixa** (manutenção).

| # | Componente | Problema | Gravidade | Evidência |
|---|---|---|---|---|
| P1 | Escuta | Não trata `onNotificationRemoved`: tocar na mensagem original **não para** o som (o pedido principal desta versão). | Alta | Classe sem esse método |
| P2 | Notificação da sirene | Tocar nela abre o app em vez de parar o som e ir para a mensagem. Não tem tela cheia, então com o celular bloqueado não aparece nada grande para parar. | Alta | `NotificationHelper.mostrarSireneTocando` sem `setFullScreenIntent` |
| P3 | Som | Toca o MP3 **uma vez** (17 s) e para. Sem repetição, sem tempo máximo configurável, sem vibração, sem trava de CPU. Com o celular no bolso, 17 s podem passar despercebidos. | Crítica | `SirenePlayer.tocar` sem `isLooping` |
| P4 | Escuta | Qualquer notificação do Business dispara: resumos de grupo, atualizações silenciosas da mesma conversa, chamadas em andamento. O único filtro é "3 s desde a última sirene". | Alta | `onNotificationPosted$1` |
| P5 | Escuta | Chamadas de voz do Business chegando não disparam alerta de forma intencional; mensagens antigas reapresentadas (após reiniciar ou ficar sem internet) disparam. | Média | Sem leitura de categoria nem do horário da mensagem |
| P6 | Escuta | Para **cada notificação de qualquer app** cria um `CoroutineScope` novo e faz uma consulta ao Room. Custo de CPU/bateria proporcional ao volume total de notificações do celular. | Média | `onNotificationPosted` |
| P7 | Escuta | Nunca pede para religar (`requestRebind`) quando o sistema desconecta a escuta. Em HyperOS isso acontece; a sirene para de funcionar até alguém abrir o app. | Crítica | Sem `requestRebind` em todo o app |
| P8 | Escuta / privacidade | Registra no histórico as notificações do **WhatsApp pessoal** (só o fato, não o texto), contrariando a regra original de nunca monitorar o pessoal. | Média | `"WhatsApp comum"` em `onNotificationPosted$1` |
| P9 | Automação | A troca do som dentro do WhatsApp por Acessibilidade depende do layout do WhatsApp, precisa da tela desbloqueada e lê o conteúdo das janelas do Business. Com a sirene do próprio app ela é **desnecessária** e é o maior risco de privacidade e de quebra. | Alta | `WhatsAppAutomationService`, `canRetrieveWindowContent=true` |
| P10 | Orquestrador | O início do plantão pode esperar até **45 s** pela automação dentro do receiver do alarme. Fica no limite do que o Android tolera para um receiver e, com a tela bloqueada (o caso normal num plantão noturno), a automação não tem tela para navegar. | Alta | `withTimeoutOrNull(45000)` chamado a partir de `AlarmReceiver` com `goAsync()` |
| P11 | Agenda | Modo Trabalho fixo em 07:50–17:00, seg–sex, no código. Inviável para outras pessoas. | Alta | `LocalTime.of(7, 50)` e `LocalTime.of(17, 0)` |
| P12 | Agenda | A repetição semanal depende de o alarme de fim rodar para criar a próxima ocorrência; se o celular estiver desligado no fim, a repetição se perde. O campo `status` gravado (FUTURO, EM_ANDAMENTO…) pode ficar desatualizado. | Alta | Consultas `status = 'FUTURO'` e lógica de fim (padrão herdado da v2) |
| P13 | Volume | Ao fim do plantão o volume vai para um valor fixo (`volumePosPlantao`), não volta ao que estava antes. | Média | Campo `volumePosPlantao` |
| P14 | Dados | `fallbackToDestructiveMigration()`: qualquer mudança de esquema numa atualização **apaga todos os plantões e o histórico**. Não há exportação de configurações. | Alta | `AppModule.db` |
| P15 | Distribuição | APK de depuração (`debuggable=true`) publicado como versão para outras pessoas: maior, mais lento, permite inspeção por `adb`. `versionCode 1` e nome `2.5.0-diag-design` não batem com a tag `v1.0.0`. | Média | Manifesto |
| P16 | Permissões | `READ_MEDIA_AUDIO` sem necessidade (o seletor do sistema já dá acesso ao arquivo escolhido); WorkManager incluído sem uso, trazendo `ACCESS_NETWORK_STATE` e 9 componentes. | Baixa | Manifesto + ausência de uso no código |
| P17 | Som | `sirene_plantao.mp3` sem metadados de origem (gerado por ffmpeg). Para distribuir publicamente, a licença precisa ser conhecida. | Média | `res/raw/sirene_plantao.mp3` |
| P18 | Tela cheia | Android 14+ exige que o app possa usar intenção de tela cheia; o app não declara nem verifica. | Média | Manifesto |
| P19 | Arquitetura | Hilt + Room + kapt/KSP para um app pequeno; lógica de negócio misturada com Android no orquestrador de 1.400 linhas; sem testes. | Baixa | Estrutura do APK |
| P20 | Interface | Status em texto corrido, botões sem hierarquia, permissões espalhadas, sem primeira utilização guiada, termos técnicos ("Sentinela", "DND", "w4b"). | Média | Textos das telas |

### 2.3 Crítica às decisões anteriores (inclusive minhas)

- **Fase 1 (Acessibilidade):** foi a decisão errada desde o início e eu deveria ter
  insistido mais. Ela tenta controlar a interface de outro app — o próprio documento
  admitia que quebra a cada atualização.
- **v2 (Sentinela):** acertou a arquitetura, mas prometeu "nunca ler conteúdo" como
  regra absoluta. Isso impede filtrar por grupo ou contato, que é exatamente o que um
  plantonista precisa ("só o grupo Plantão TI"). A v3 mantém o padrão de não ler, mas
  permite filtros opcionais, processados só na memória.
- **v2:** o volume pós-plantão fixo e a repetição semanal criada no fim do plantão
  foram bugs meus. Eles seguem na versão publicada.
- **2.5.0:** reintroduziu a Acessibilidade e manteve as duas abordagens convivendo,
  o que duplica a complexidade sem ganho.

---

## 3. Planejamento

### 3.1 Alternativas avaliadas

| Decisão | Opções | Escolha | Por quê |
|---|---|---|---|
| Como perceber a mensagem | Acessibilidade; acesso às notificações | **Acesso às notificações** | API pública feita para isso, funciona com a tela bloqueada, não depende do layout do WhatsApp. |
| Como alertar | Trocar o som do WhatsApp; tocar som próprio | **Som próprio no canal de alarme** | Toca no silencioso, volume controlado, repetição e confirmação verificáveis. |
| Parar ao tocar na mensagem | Só o botão do app; observar a remoção da notificação original | **Os dois** | O Android informa o motivo da remoção (`REASON_CLICK`, `REASON_APP_CANCEL`…) a partir do Android 8. Ver limites na seção 6. |
| Guardar configurações | Room; DataStore; arquivo JSON | **Arquivo JSON com gravação atômica (`AtomicFile`)** | Poucos dados estruturados, tolerância a arquivo corrompido e evolução de esquema sem apagar nada. *Mudou durante a implementação:* o plano era DataStore, mas a escuta de notificações pode ser recriada pelo sistema no exato momento em que uma notificação chega, e precisa da configuração na hora. O DataStore só entrega o primeiro valor de forma assíncrona; o `AtomicFile` é lido de forma síncrona uma vez e fica em memória, com a mesma garantia de gravação atômica. |
| Guardar histórico | Room; arquivo de linhas | **Arquivo de linhas (JSONL) com limite** | Só inserção, até 2.000 eventos, busca em memória é instantânea. Room seria justificado com dezenas de milhares de registros. |
| Injeção de dependências | Hilt; contêiner manual | **Contêiner manual** | 10 objetos de vida longa. Hilt acrescenta processamento de anotações e tempo de build sem ganho real aqui. |
| Interface | Views; Compose | **Compose + Material 3** | A 2.5.0 já usa Compose; não há migração a fazer, só redesenho. |
| Manter o app vivo | Serviço permanente; serviço só no plantão | **Só no plantão** + escuta religável | Fora do plantão, a escuta (que o sistema mantém) basta; nada roda à toa. |
| Internet | Com; sem | **Sem permissão de internet** | Garantia verificável no manifesto de que nada sai do celular. Atualizações: link para o GitHub. |
| Assinatura | Chave de depuração; chave de produção | **Chave de produção** (ver seção 7) | Necessária para distribuição pública e atualizações seguras. |

### 3.2 Tabela de melhorias

Prioridade: **P0** entra antes de qualquer release; **P1** entra na 3.0; **P2** depois.

| Componente | Problema | Gravidade | Melhoria | Justificativa técnica | Prioridade |
|---|---|---|---|---|---|
| Escuta | P1, P4, P5, P6 | Alta | Núcleo de decisão puro e testável: ignora resumos de grupo, atualizações silenciosas (mesma chave e mesmo horário, ou `FLAG_ONLY_ALERT_ONCE`), serviços e progresso; trata chamada chegando (categoria `call` com tela cheia); ignora mensagens anteriores ao início do plantão. Configuração lida da memória, sem banco por notificação. | Decisão em microssegundos e reproduzível em teste unitário. | P0 |
| Parar o alerta | P1, P2 | Alta | Para o som quando: a notificação original é tocada (`REASON_CLICK`), dispensada ou fechada pelo app de origem; quando você toca na notificação do Plantão Seguro (para e abre a conversa original); no botão Parar; nos botões de volume na tela da sirene. | Usa somente eventos oficiais do Android. | P0 |
| Som | P3 | Crítica | Repetição contínua até parar, tempo máximo, número de repetições, volume crescente, vibração (6 padrões), lanterna opcional, trava de CPU, foco de áudio. Um só alarme por vez; novos alertas somam no mesmo. | É a função central do app. | P0 |
| Tela da sirene | P2, P18 | Alta | Tela cheia por cima da tela bloqueada, botão grande, "parar e abrir a conversa". Verificação da permissão de tela cheia no Android 14+. | Uso meio dormindo. | P0 |
| Religar escuta | P7 | Crítica | `requestRebind` na desconexão, no boot, ao abrir o app e numa verificação a cada 30 min durante o plantão; alerta se continuar desligada. | Única forma oficial de recuperar a escuta. | P0 |
| Agenda | P11, P12, P13 | Alta | Agenda calculada (dias da semana + horários), sem estado gravado de "em andamento"; Modo Trabalho vira um plantão comum configurável; folga = pular uma ocorrência; ao terminar, o som volta **como estava**. | Resultado igual para o mesmo horário, imune a alarme perdido ou reinício. | P0 |
| Acessibilidade | P9, P10 | Alta | Remover a automação e o modo de captura de rótulos. | Deixa de ser necessária; elimina o maior risco de privacidade e de quebra. | P0 |
| Regras | — | — | Regras por app com filtros opcionais por contato/grupo e palavras, exceções, som próprio, prioridade, silenciar por um tempo, valer só no plantão ou sempre. | Pedido do produto. Filtros só leem o texto quando você cria um. | P1 |
| Confirmação | — | — | Cada alerta fica registrado como confirmado (por quem) ou não confirmado; opção de tocar de novo enquanto a mensagem não for aberta; confirmado não volta a tocar pela mesma mensagem. | Pedido do produto. | P1 |
| Biblioteca de sons | P17 | Média | 22 sons próprios gerados por síntese (sem direitos de terceiros), em 7 categorias, com prévia; sons do celular; importar arquivo. O MP3 antigo sai por origem desconhecida. | Distribuição legal. | P1 |
| Dados | P14 | Alta | Arquivo JSON atômico com esquema versionado e campos com valor padrão; exportar/importar configurações; importação automática dos plantões da 2.5.0 quando a atualização for por cima. | Atualizar sem perder nada. | P0 |
| Diagnóstico | P7, P20 | Alta | Tela de diagnóstico com cada verificação, "testar a escuta" ponta a ponta (o app publica uma notificação de teste e confirma que a escuta percebeu), teste com tela bloqueada, dicas por fabricante. | Pedido do produto; ataca a causa nº 1 de falha (fabricante matando o app). | P1 |
| Permissões | P16, P18 | Baixa | Remover `READ_MEDIA_AUDIO`, WorkManager e `ACCESS_NETWORK_STATE`; adicionar `USE_FULL_SCREEN_INTENT`, `VIBRATE`, isenção de bateria. | Mínimo necessário. | P0 |
| Privacidade | P8 | Média | Só os apps escolhidos são considerados; nada do WhatsApp pessoal entra no histórico a menos que você o escolha; política de privacidade no app e no repositório. | Clareza e confiança. | P0 |
| Interface | P20 | Média | Redesenho completo (seção 5), primeira utilização guiada, ajuda no app. | "Menos complicação." | P1 |
| Arquitetura | P19 | Baixa | Núcleo em Kotlin puro (agenda, filtro, regras, alertas) separado das classes Android; testes automatizados. | Testável e fácil de manter. | P1 |
| Distribuição | P15 | Média | Build de release assinado, minificado, não depurável; versão 3.0.0 (`versionCode 300`); GitHub Actions compila, testa e publica. | Pedido do produto. | P0 |

---

## 4. Arquitetura da v3

```
Notificação de outro app
        │
        ▼
SentinelaService (adaptador Android, fino)
        │  metadados + texto só se houver filtro
        ▼
Motor de decisão (Kotlin puro: regras, filtro, duplicados, plantão ativo)
        │  decisão: ignorar | alertar(regra) | somar ao alerta atual
        ▼
CentralDeAlertas (estado do alerta: tocando → confirmado/não confirmado)
        │
        ├── Sirene (MediaPlayer no canal de alarme, vibração, lanterna)
        ├── Notificador (canais, tela cheia, ações)
        └── Histórico (JSONL, sem conteúdo de mensagens)

AlarmManager ──► Coordenador ──► Agenda (Kotlin puro)
                     │
                     ├── ControleSom (silencioso, Não Perturbe, volumes, restaurar)
                     └── PlantaoService (primeiro plano só durante o plantão)
```

Princípios:

1. **Calcular, não lembrar.** O que está valendo é sempre recalculado da agenda e do
   relógio. Estado gravado só para o que não dá para recalcular (como estava o som
   antes do plantão).
2. **Núcleo sem Android.** Agenda, regras, filtro e máquina de estados do alerta são
   Kotlin puro e testados na JVM.
3. **Um alarme por vez.** Novos alertas durante um alerta só aumentam a contagem.
4. **Falhar à vista.** Toda falha vira notificação de problema e linha no histórico.

---

## 5. Redesenho

### 5.1 Navegação

Barra inferior com quatro áreas:

| Área | Para quê |
|---|---|
| **Início** | Responder num relance "estou protegido agora?": estado do plantão, alerta pendente, problemas, próximos 7 dias e plantões. |
| **Alertas** | Regras do que dispara a sirene e como ela toca. |
| **Histórico** | O que aconteceu, com busca e filtros. |
| **Ajustes** | Comportamento geral, diagnóstico, permissões, backup, ajuda, privacidade, versão. |

Telas abertas por cima: editor de plantão, editor de regra, biblioteca de sons,
escolha de apps, diagnóstico, permissões por fabricante, ajuda, privacidade,
primeiros passos e a **tela da sirene** (cheia, por cima da tela bloqueada).

### 5.2 Primeira utilização guiada

1. **O que o app faz e o que não faz** (toca só no plantão; não lê suas mensagens a
   menos que você crie um filtro; não usa internet).
2. **De onde vêm as mensagens de trabalho** (apps instalados, Business pré-marcado).
3. **Permissões essenciais**, uma por vez, cada uma com uma frase de por quê.
4. **Ajuste do fabricante** (Xiaomi/POCO, Samsung, Motorola…), com atalho direto.
5. **Som e teste**, inclusive o teste com a tela bloqueada.
6. **Primeiro plantão** ou "começar agora".

### 5.3 Decisões de UX

- **O estado vem primeiro.** Uma luz de viatura (giroflex) apagada, girando em
  laranja (sirene armada) ou vermelha (tocando), com uma frase. Ninguém precisa ler
  uma lista de status para saber se está protegido.
- **"Quando" separado de "o quê".** Plantões dizem quando a proteção vale; regras
  dizem o que dispara e como toca. Para quem começa existe uma regra pronta
  (WhatsApp Business, qualquer mensagem, sirene clássica), então a pessoa só vê esse
  conceito se quiser.
- **Feito para quem acorda assustado.** Na tela da sirene, um botão de 196 dp,
  botões de volume param, texto curto, "parar e abrir a conversa" em um toque.
- **Escala de plantão como no papel.** Os próximos 7 dias em colunas, com os plantões
  pintados — é como plantonista já pensa a semana.
- **Linguagem simples.** Nada de "Sentinela", "DND", "w4b" ou "listener".
- **Cor com significado.** Laranja só para "plantão/sirene", vermelho só para
  "tocando/problema"; o resto da interface é calmo. Azul-noite e laranja vêm da
  sinalização de emergência (Defesa Civil).
- **Acessibilidade.** Alvos de pelo menos 48 dp, texto em `sp` (respeita o tamanho de
  fonte do sistema), contraste mínimo 4,5:1, descrições para leitor de tela, estado
  nunca indicado só por cor.

---

## 6. Limites reais (o que o Android permite observar)

- O app **não sabe** se você leu a mensagem. Ele sabe que a notificação original foi
  **tocada** (`REASON_CLICK`), **dispensada** (`REASON_CANCEL`) ou **removida pelo
  próprio WhatsApp** (`REASON_APP_CANCEL`). A última costuma acontecer quando você abre
  a conversa — no celular ou no WhatsApp Web — mas também pode acontecer se a
  mensagem for apagada. A v3 trata as três como "você viu" (configurável) e chama isso
  de "parou porque a notificação foi aberta ou dispensada", não de "mensagem lida".
- Filtros por contato, grupo ou palavra dependem do que cada app coloca na
  notificação. Se o WhatsApp mostrar "2 mensagens novas" em vez do texto, o filtro de
  palavra não tem o que comparar. Se você esconder o conteúdo das notificações no
  WhatsApp, os filtros deixam de funcionar.
- Fabricantes como Xiaomi/POCO podem encerrar o app mesmo com tudo liberado. A v3
  detecta e avisa, mas não consegue impedir. O ajuste manual do fabricante é
  necessário.
- No modo "silêncio total" do Não Perturbe, nem alarmes tocam. O plantão pode
  desligar o Não Perturbe (com permissão); regras "sempre ativas" fora do plantão
  respeitam o Não Perturbe.
- Abrir a conversa exata do WhatsApp a partir da tela da sirene usa o atalho da
  própria notificação original. Se o sistema não permitir, o app abre o WhatsApp na
  tela inicial.

---

## 7. Assinatura e atualização a partir da v1.0.0

Fato observado: o APK v1.0.0 é assinado com **certificado de depuração** ("Android
Debug", esquema v2), criado em 11/07/2026 no computador onde foi compilado.

- SHA-256 do certificado:
  `E2:73:A8:BC:6E:1A:0A:9B:07:5B:FF:7B:F5:AA:44:DB:EF:8D:97:1F:42:F9:F2:6A:6B:9F:1A:22:ED:25:4D:49`
- Pacote `com.plantaoseguro`, `versionCode 1`.

Consequência: o Android só instala uma atualização por cima se ela for assinada com
a **mesma** chave. Essa chave existe apenas naquele computador
(`C:\Users\<usuário>\.android\debug.keystore`).

| Caminho | Atualiza por cima? | Mantém os plantões? | Observação |
|---|---|---|---|
| **A.** Nova chave de produção (recomendado) | Não: desinstalar a v1.0.0 uma vez | Não (recadastrar; são poucos) | Chave guardada criptografada no repositório e liberada por um segredo do GitHub. Daqui para frente toda atualização instala por cima. |
| **B.** Usar a chave de depuração antiga | Sim | Sim (importação automática da 2.5.0) | Exige o arquivo `debug.keystore` daquele computador. Funciona, mas é uma chave de depuração com senha pública ("android"); não serve para loja. |

A v3 traz exportação/importação de configurações para que uma troca futura de chave
nunca mais custe os dados.

---

## 8. Testes

### 8.1 Mapa dos 15 cenários pedidos

| # | Cenário | Como será testado |
|---|---|---|
| 1 | Notificação relevante dispara | Unitário (motor) + emulador (notificação real via `cmd notification post`) |
| 2 | Irrelevante não dispara | Unitário + emulador |
| 3 | Duplicadas não duplicam | Unitário (mesma chave/horário, resumo de grupo, `ONLY_ALERT_ONCE`) |
| 4 | Tocar no alerta para | Unitário (motivo `REASON_CLICK`) + emulador (UiAutomator toca a notificação) |
| 5 | Parar manualmente | Robolectric (ação da notificação) + emulador |
| 6 | Várias seguidas | Unitário (soma no alerta atual) |
| 7 | Celular bloqueado | Emulador (tela desligada) — **validação final só em aparelho real** |
| 8 | App em segundo plano | Emulador |
| 9 | Serviço interrompido | Emulador (`am kill` + religar) — HyperOS **só em aparelho real** |
| 10 | Reinício | Robolectric (receptor de boot reagenda) — reinício físico **só em aparelho real** |
| 11 | Perde permissão de notificações | Robolectric + diagnóstico |
| 12 | Troca o som de uma regra | Unitário (configuração) + Robolectric |
| 13 | Importa áudio | Robolectric (URI persistida) — seletor real **só em aparelho** |
| 14 | Silencioso / Não Perturbe | Emulador (`cmd notification set_dnd`) |
| 15 | Atualiza sem perder | Unitário (JSON antigo lido pela versão nova; importação do banco 2.5.0) |

O relatório final separa: executado na JVM, executado em emulador, e **ainda não
validado em aparelho real**.

---

## 9. Ordem de implementação

1. Núcleo puro (agenda, regras, filtro, alertas) com testes.
2. Escuta, sirene, tela cheia, notificações, religamento.
3. Dados (arquivo JSON atômico), migração da 2.5.0, backup.
4. Telas e primeira utilização.
5. GitHub Actions: compilar, testar, capturas de tela, emulador.
6. Release 3.0.0 só depois dos testes e da decisão da seção 7.
