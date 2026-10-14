#!/usr/bin/env bash
# Teste de fumaça do APK de RELEASE (minificado pelo R8) num emulador.
#
# Os cenários completos rodam no APK de debug. Este roteiro confere que o APK
# de release, do jeito que vai para as pessoas, abre, lê e grava as
# configurações e toca o alerta, usando só o adb (sem instrumentação).
#
# Uso: teste-release.sh <apk-release> <apk-simulador> <pasta-de-saida>
# Requer um emulador com "adb root" (imagens google_apis).

set -u
APK="$1"
SIM_APK="$2"
OUT="$3"
P=com.plantaoseguro
SIM=com.plantaoseguro.simulador
mkdir -p "$OUT"
REL="$OUT/release.md"
: > "$REL"
falhas=0

anotar() { echo "$*" | tee -a "$REL"; }
ok() { anotar "| $1 | passou | $2 |"; }
falhou() { anotar "| $1 | **falhou** | $2 |"; falhas=$((falhas + 1)); }
tela() { adb shell uiautomator dump /sdcard/tela.xml > /dev/null 2>&1; adb shell cat /sdcard/tela.xml 2>/dev/null; }
foto() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
agora_ms() { echo $(( $(adb shell date +%s | tr -d '\r') * 1000 )); }
ativas() { adb shell cmd notification list 2>/dev/null | tr -d '\r'; }

anotar "# Teste de fumaça do APK de release"
anotar ""
anotar "APK: $(basename "$APK") ($(sha256sum "$APK" | cut -c1-16)…)"
anotar ""
anotar "| Passo | Resultado | Observado |"
anotar "|---|---|---|"

adb root > /dev/null 2>&1
sleep 3
adb wait-for-device
adb uninstall "$P" > /dev/null 2>&1
adb install -r "$SIM_APK" > /dev/null
if adb install -r "$APK" > "$OUT/instalacao.txt" 2>&1; then
  ok "Instalar" "$(tr -d '\r' < "$OUT/instalacao.txt" | tail -1)"
else
  falhou "Instalar" "$(tr -d '\r' < "$OUT/instalacao.txt" | tail -1)"
  exit 1
fi

adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell pm grant $SIM android.permission.POST_NOTIFICATIONS
adb shell appops set $P USE_FULL_SCREEN_INTENT allow
adb shell cmd notification allow_dnd $P
adb shell cmd notification allow_listener $P/com.plantaoseguro.sentinela.SentinelaService
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c

# 1. Primeira abertura
adb shell am start -W -n $P/.ui.MainActivity > /dev/null
sleep 4
if tela | grep -q 'Começar'; then ok "Primeira abertura" "Primeiros passos apareceram."; else falhou "Primeira abertura" "Primeiros passos não apareceram."; fi
foto release_1_primeira_abertura

# 2. Configurações gravadas no formato do app (como uma cópia restaurada):
#    regra para o simulador e um plantão valendo agora.
adb shell am force-stop $P
t=$(agora_ms)
cat > "$OUT/dados.json" <<EOF
{"versao":3,
 "regras":[{"id":"fumaca","nome":"Fumaça","apps":["$SIM"],
            "toque":{"som":{"type":"embutido","id":"curto_ping"},"volume":10,"vibracao":"NENHUMA","duracaoSeg":60}}],
 "plantoes":[{"id":"agora","nome":"Plantão de teste","tipo":"UNICO","inicioMs":$((t - 60000)),"fimMs":$((t + 3600000)),
              "tirarDoSilencioso":false,"desligarNaoPerturbe":false}],
 "ajustes":{"boasVindasConcluida":true}}
EOF
adb push "$OUT/dados.json" /data/local/tmp/dados.json > /dev/null
uid=$(adb shell stat -c %u /data/data/$P | tr -d '\r')
adb shell "mkdir -p /data/data/$P/files && cp /data/local/tmp/dados.json /data/data/$P/files/dados.json && chown $uid:$uid /data/data/$P/files /data/data/$P/files/dados.json && chmod 600 /data/data/$P/files/dados.json && restorecon -R /data/data/$P/files"
adb shell am start -W -n $P/.ui.MainActivity > /dev/null
sleep 5
if tela | grep -q 'Em plantão'; then ok "Ler as configurações" "Tela inicial mostrou \"Em plantão\"."; else falhou "Ler as configurações" "A tela inicial não mostrou o plantão."; fi
foto release_2_em_plantao
adb shell input keyevent KEYCODE_HOME

# 3. Mensagem do app vigiado: o alerta toca
sleep 2
adb shell am broadcast -n $SIM/.Comando -f 32 --es acao postar --ei id 1 --es texto Release_msg1 --el quando "$(agora_ms)" > /dev/null
sleep 5
if ativas | grep -q "|$P|2002|"; then ok "Mensagem dispara o alerta" "Notificação do alerta publicada."; else falhou "Mensagem dispara o alerta" "Sem notificação do alerta."; fi
foto release_3_alerta

# 4. O app de mensagens fecha a notificação: o alerta para
adb shell am broadcast -n $SIM/.Comando -f 32 --es acao cancelar --ei id 1 > /dev/null
sleep 4
if ativas | grep -q "|$P|2002|"; then falhou "Fechar a mensagem para o alerta" "O alerta continuou."; else ok "Fechar a mensagem para o alerta" "Notificação do alerta saiu."; fi

# 5. Gravou o histórico e relê tudo depois de o processo ser encerrado
adb shell am force-stop $P
adb shell am start -W -n $P/.ui.MainActivity > /dev/null
sleep 4
if tela | grep -q 'Em plantão'; then ok "Reabrir depois de encerrado" "Configurações relidas do arquivo."; else falhou "Reabrir depois de encerrado" "Não voltou ao estado salvo."; fi
if adb shell cat /data/data/$P/files/historico.jsonl 2>/dev/null | grep -q 'Alerta confirmado'; then
  ok "Histórico gravado" "Alerta e confirmação registrados."
else
  falhou "Histórico gravado" "Histórico sem o alerta."
fi
foto release_4_reaberto

# 6. Nenhum travamento
adb logcat -d -v time > "$OUT/logcat-release.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $P" "$OUT/logcat-release.txt" > "$OUT/travamentos.txt"; then
  falhou "Sem travamentos" "Veja travamentos.txt."
else
  ok "Sem travamentos" "Nenhum FATAL EXCEPTION no logcat."
fi

anotar ""
anotar "Falhas: $falhas"
[ "$falhas" -eq 0 ]
