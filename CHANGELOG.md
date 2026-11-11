# Mudanças

As versões seguem `MAIOR.MENOR.CORREÇÃO`. O APK v1.0.0 publicado em
Releases é, por dentro, a versão 2.5.0 do código (veja `docs/AUDITORIA.md`).

## 3.0.0-beta.1 — versão de teste

Reescrita completa a partir da auditoria da 2.5.0. É uma versão de teste:
a v1.0.0 continua sendo a versão principal até a 3.0.0 ser confirmada em
celulares de verdade.

**Atenção na instalação:** a v1.0.0 foi assinada com uma chave de
desenvolvimento que não está disponível. A 3.0.0 usa a chave definitiva do
projeto, então é preciso **desinstalar a v1.0.0 antes** de instalar a nova.
Os plantões precisam ser cadastrados de novo.

### Alertas
- Regras de alerta: escolha os apps (WhatsApp Business, WhatsApp, Telegram,
  Teams, Slack, qualquer app instalado), limite a contatos ou grupos, exija
  palavras ("urgente") ou ignore palavras ("bom dia").
- Regras "no plantão" ou "sempre", ligadas a todos ou a alguns plantões, e
  regra crítica (avaliada primeiro e toca até durante uma ligação).
- **Tocar ou dispensar a notificação original para o alerta** (ajustável). O
  app não sabe se você leu a mensagem: ele usa o aviso do Android de que a
  notificação foi tocada, dispensada ou fechada pelo app de mensagens.
- Tela cheia por cima do bloqueio, com botão Parar grande, "Parar e abrir a
  conversa" e "Silenciar esta regra por 1 hora"; botões de volume também param.
- Um alarme por vez: mensagens novas somam no alerta que já toca.
- Mensagem confirmada não volta a tocar; atualizações da mesma notificação,
  mensagens antigas e mensagens de antes do plantão começar são ignoradas.
- Alerta sem confirmação fica marcado e pode tocar de novo a cada X minutos
  enquanto a mensagem continuar sem ser aberta.
- Silenciar uma regra por um tempo.

### Sons
- 22 sons próprios em 7 grupos: sirenes, alarmes intensos, estilo hospitalar,
  eletrônicos, progressivos, discretos e notificações curtas.
- Também toques de alarme do celular e arquivos de áudio seus.
- Prévia, volume do alerta, volume crescente, duração por tempo, por
  repetições ou até parar (com limite de segurança de 20 minutos),
  6 padrões de vibração, só vibração e lanterna piscando.

### Plantões
- Semanais (dias e horários, inclusive virando a noite) ou de uma vez só.
- Escala dos próximos 7 dias na tela inicial.
- Folga na próxima vez, encerrar agora, começar agora.
- Tirar do silencioso e do Não Perturbe no início e voltar como estava no fim.
- Vigia de volume (do antigo Modo Trabalho) por plantão.
- Os plantões da 2.5.0 são importados quando a 3.0.0 é instalada por cima
  dela com a mesma assinatura.

### Confiabilidade
- O estado é recalculado a partir da agenda (não depende de lembrar o que
  aconteceu), então reinícios, alarmes perdidos e mudança de fuso se corrigem.
- Diagnóstico com teste de ponta a ponta da escuta e teste com a tela bloqueada.
- Verificação a cada 30 minutos durante o plantão; se o Android desligar a
  escuta, o app tenta religar e avisa.
- Histórico com filtro, busca e exportação (texto ou CSV).

### Privacidade
- Sem permissão de internet. O texto das mensagens só é conferido, na
  memória, quando a regra tem filtro, e nunca é gravado.
- Nada vai para o backup na nuvem. Cópia de configurações só quando você pede.

### Por dentro
- Kotlin, Jetpack Compose e Material 3; núcleo de decisão em Kotlin puro.
- 94 testes automáticos na JVM (lógica, dados e 24 telas fotografadas) e
  cenários de ponta a ponta num emulador Android 14 (`docs/TESTES.md`).
- Compilação, testes e assinatura no GitHub Actions; chave guardada
  criptografada (`assinatura/LEIA.md`).

### Removido em relação à 2.5.0 (e por quê)
- **Automação por Acessibilidade** (`WhatsAppAutomationService`): lia as
  janelas do WhatsApp Business para trocar o som de notificação dentro dele.
  Dependia do layout do WhatsApp e da tela desbloqueada, e era o maior risco
  de privacidade do app. Com a sirene tocando pelo próprio Plantão Seguro,
  deixou de ser necessária.
- **Banco Room, Hilt e WorkManager:** substituídos por um arquivo de
  configurações com gravação atômica, dependências montadas à mão e alarmes do
  sistema: menos peças, leitura imediata quando uma notificação chega.
- **Modo Trabalho fixo (seg a sex, 07:50–17:00):** virou um plantão comum
  chamado "Expediente", editável, com o mesmo vigia de volume (30 min, 1 h,
  2 h ou até o fim, e "voltar agora").
