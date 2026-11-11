# Política de privacidade do Plantão Seguro

Versão 3.0 · vale a partir da versão 3.0.0 do aplicativo

O Plantão Seguro funciona inteiramente no seu celular. Ele não tem servidor,
conta, anúncios nem rastreamento, e **não pede permissão de acesso à
internet**: o Android não deixa um app sem essa permissão enviar dados para
fora do aparelho.

## 1. O que o app recebe

Para tocar o alerta, o app precisa do **acesso às notificações** do Android.
Com esse acesso, o sistema entrega ao app as notificações que aparecem no
celular. O que o Plantão Seguro faz com elas:

| Situação | O que é usado | O que acontece depois |
|---|---|---|
| Notificação de um app que nenhuma regra vigia | Só o nome do pacote do app, para saber que não é vigiado | Descartada na hora |
| Notificação de um app vigiado, regra **sem** filtro | Nome do app, horário, se é chamada, se é atualização da mesma notificação | Usado para decidir se toca; nada do conteúdo é lido |
| Notificação de um app vigiado, regra **com** filtro de contato, grupo ou palavra | Também o título, o nome da conversa, o remetente e o texto da notificação | Comparado na memória com os seus filtros e descartado em seguida |

O texto das mensagens **nunca é gravado** em arquivo, no histórico ou em
registros de erro (logs), e nunca sai do celular.

O nome da conversa aparece na tela do alerta só quando a regra tem filtro de
contato ou grupo, e só com o celular desbloqueado. Com o celular bloqueado, a
tela do alerta mostra o app de origem e o nome da regra, sem a conversa, e as
notificações do app mostram só um título genérico ("Mensagem de trabalho").

O app não sabe se você leu a mensagem. Ele só recebe do Android o aviso de que
a notificação original foi tocada, dispensada ou fechada pelo próprio app de
mensagens, e usa isso para parar o alerta.

## 2. O que fica guardado no celular

Na pasta privada do app (que outros apps não acessam):

- **Configurações:** regras de alerta, plantões, ajustes e o estado de
  execução (por exemplo, como o som do celular estava antes do plantão, para
  poder voltar ao normal no fim).
- **Histórico:** fatos como "plantão começou às 19:00", "alerta tocou às
  03:12 no WhatsApp Business", "parado ao tocar na mensagem". Guarda o nome
  do app e o nome da regra, **nunca** o texto, o remetente ou o grupo. Os
  registros mais velhos que o prazo escolhido em Ajustes (30 dias, por
  padrão) são apagados automaticamente, e o total nunca passa de 2.000.

Se você escolher um som de um arquivo do celular, o app guarda o endereço
desse arquivo e a permissão de leitura que o Android concede para ele.

Nada disso entra no backup automático na nuvem do Google nem é copiado na
transferência para outro celular. Para levar as configurações, use
**Ajustes > Guardar uma cópia**, que gera um arquivo que só você guarda.
Essa cópia leva regras, plantões e ajustes; não leva o histórico.

## 3. O que você controla

- **Apagar o histórico:** Histórico > Apagar.
- **Parar de vigiar um app:** desligue ou edite a regra.
- **Tirar o acesso às notificações:** Configurações do Android > Notificações
  > Acesso às notificações (o nome exato muda conforme o fabricante).
- **Apagar tudo:** desinstalar o app apaga configurações e histórico.

## 4. Permissões

Cada permissão e o motivo dela estão em [PERMISSOES.md](PERMISSOES.md).

## 5. Compartilhamento

O app não compartilha dados com ninguém. Ele só gera arquivos quando você
pede (exportar o histórico ou guardar uma cópia das configurações), e você
escolhe para onde eles vão pela tela de compartilhamento do Android.

## 6. Crianças

O app é uma ferramenta de trabalho para adultos e não foi feito para crianças.

## 7. Mudanças nesta política

Mudanças são publicadas neste arquivo, no repositório do projeto, junto com a
versão do app que as traz.

## 8. Contato

Dúvidas ou problemas: abra uma *issue* em
https://github.com/pdrjsampaio/plantao-seguro/issues
