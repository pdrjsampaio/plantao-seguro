# Testes no emulador

Aparelho: Google sdk_gphone64_x86_64, Android 14 (API 34).
Tela cheia liberada por padrão para um app instalado fora da loja: sim.

| Teste | Resultado | Observado |
|---|---|---|
| c01_mensagem_no_plantao_dispara | passou | App em segundo plano, tela ligada: alerta tocou e a notificação do alerta apareceu. |
| c02_irrelevantes_nao_disparam | passou | App não vigiado, mensagem de 13 h atrás, mensagem de antes do plantão e mensagem fora do plantão: nenhuma tocou. |
| c03_duplicadas_nao_duplicam | passou | Republicação igual não somou nem voltou a tocar depois de confirmada; mensagem nova na mesma conversa tocou. |
| c04_tocar_na_notificacao_original_para | passou | Tocar na notificação do app de mensagens (motivo REASON_CLICK) parou o alerta e abriu a conversa. |
| c05_parar_pela_notificacao_do_alerta | passou | Alerta parou tocando no botão Parar da notificação. |
| c06_varias_mensagens_um_alarme_so | passou | Três mensagens seguidas viraram um alerta só, com contador 3. |
| c07_tela_bloqueada_mostra_o_alerta | passou | Tela apagada e bloqueada (bloqueio=true): a tela do alerta abriu por cima, tela acesa=true; Parar funcionou. |
| c07b_botao_de_volume_para | passou | Com a tela do alerta aberta, o botão de volume parou o alerta. |
| c09_escuta_desligada_e_religada | passou | Sem acesso às notificações: diagnóstico mostrou erro e o app publicou o aviso de problema; ao religar, voltou a tocar. |
| c10_recalcula_depois_de_reiniciar | passou | Com os alarmes apagados, o receptor de sistema reagendou o início do plantão (2026-10-14T10:48:00Z). |
| c12_trocar_o_som_da_regra | passou | A regra com a Sirene rápida tocou com esse som. |
| c13_som_de_arquivo_e_arquivo_apagado | passou | Som de um arquivo (content://) tocou; depois de apagar o arquivo, o alerta tocou com o som padrão. |
| c14_silencioso_e_nao_perturbe | passou | Antes: silencioso e Não Perturbe (2). No plantão: som normal e Não Perturbe desligado. No fim: tudo como antes. |
| c16_sem_confirmacao_toca_de_novo | passou | Tocou 5 s, ficou sem confirmação, voltou a tocar 1 min depois pelo AlarmManager; fechar a mensagem encerrou. |
| c17_chamada_para_quando_encerrada | passou | Chamada (categoria call com tela cheia) tocou e parou quando o app fechou a notificação, mesmo com o ajuste de parar desligado. |
| c18_ajuste_desligado_continua_tocando | passou | Com "parar ao tocar na mensagem" desligado, o alerta continuou até alguém parar. |
| c19_filtro_por_palavra | passou | Regra com a palavra "urgente": rotina não tocou; "URGENTE" tocou. |
| c20_regra_silenciada_por_um_tempo | passou | Silenciada por 1 h não tocou; reativada tocou. |
| c21_teste_da_escuta_do_diagnostico | passou | Teste ponta a ponta do diagnóstico: a escuta percebeu em 256 ms. |
| c22_teste_com_tela_bloqueada | passou | O botão "Testar bloqueado" tocou o alerta 3 s depois, por cima da tela bloqueada. |
| c23_abrir_a_conversa_pelo_alerta | passou | "Abrir conversa" (pelo botão da notificação) parou o alerta e abriu a conversa usando o atalho da notificação original. |
