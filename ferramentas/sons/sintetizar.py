"""
Sintetiza os sons embutidos do Plantão Seguro.

Todos os sons são gerados matematicamente aqui (nenhuma amostra de terceiros),
então não há direito autoral envolvido. Cada som é um loop sem emenda: a fase
termina num múltiplo exato de 2π, então o MediaPlayer pode repetir sem estalo.

Uso: python3 sintetizar.py <pasta_saida_wav>
"""
import sys
import os
import numpy as np
import soundfile as sf

SR = 44100
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)


def t_of(dur):
    return np.arange(int(round(dur * SR))) / SR


def fase_ciclica(freq):
    """Integra a frequência instantânea e ajusta levemente para fechar o loop."""
    fase = np.cumsum(freq) / SR  # em ciclos (soma inclusiva)
    # A amostra "seguinte" à última é a primeira de novo: para a onda continuar
    # sem salto, a fase acumulada até a última amostra precisa ser um número
    # inteiro de ciclos.
    total = fase[-1]
    alvo = max(1, round(total))
    return 2 * np.pi * fase * (alvo / total)


def harmonicos(fase, pesos):
    """Soma de harmônicos (limitada para não passar de ~16 kHz)."""
    s = np.zeros_like(fase)
    for k, a in pesos:
        s += a * np.sin(k * fase)
    return s


def quadrada_suave(fase, f_max):
    """Onda 'quadrada' de banda limitada (harmônicos ímpares 1/k)."""
    pesos = []
    for k in range(1, 40, 2):
        if k * f_max > 15000:
            break
        pesos.append((k, 1.0 / k))
    return harmonicos(fase, pesos)


def serra_suave(fase, f_max):
    pesos = []
    for k in range(1, 40):
        if k * f_max > 15000:
            break
        pesos.append((k, 1.0 / k))
    return harmonicos(fase, pesos)


def env_ad(n, ataque=0.004, release=0.006):
    e = np.ones(n)
    a = max(1, int(ataque * SR))
    r = max(1, int(release * SR))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] = np.minimum(e[-r:], np.linspace(1, 0, r))
    return e


def finalizar(sinal, drive=1.6, pico=0.89):
    """Saturação suave para soar mais alto + normalização de pico."""
    s = sinal / (np.max(np.abs(sinal)) + 1e-9)
    s = np.tanh(drive * s) / np.tanh(drive)
    s = s / (np.max(np.abs(s)) + 1e-9) * pico
    return s.astype(np.float32)


def salvar(nome, sinal):
    sf.write(os.path.join(OUT, nome + ".wav"), sinal, SR, subtype="PCM_16")
    print(f"{nome}: {len(sinal) / SR:.2f}s")


# ---------------------------------------------------------------- sirenes

def sirene_classica():
    # "Wail": sobe e desce devagar entre 620 e 1480 Hz, ciclo de 3,6 s.
    dur = 7.2
    t = t_of(dur)
    ciclo = 3.6
    x = (t % ciclo) / ciclo
    # sobe em 45% do ciclo, desce no resto (como sirene real: subida mais rápida)
    forma = np.where(x < 0.45, 0.5 - 0.5 * np.cos(np.pi * x / 0.45),
                     0.5 + 0.5 * np.cos(np.pi * (x - 0.45) / 0.55))
    f = 620 + (1480 - 620) * forma
    fase = fase_ciclica(f)
    s = quadrada_suave(fase, 1480) * 0.8 + np.sin(fase) * 0.4
    return finalizar(s, drive=1.4)


def sirene_rapida():
    # "Yelp": varredura rápida 720 → 1650 Hz, 3 por segundo.
    ciclos = 12
    ciclo = 0.32
    dur = ciclos * ciclo
    t = t_of(dur)
    x = (t % ciclo) / ciclo
    forma = np.where(x < 0.7, x / 0.7, 1 - (x - 0.7) / 0.3)
    forma = 0.5 - 0.5 * np.cos(np.pi * forma)
    f = 720 + (1650 - 720) * forma
    fase = fase_ciclica(f)
    s = quadrada_suave(fase, 1650) * 0.8 + np.sin(fase) * 0.4
    return finalizar(s, drive=1.5)


def sirene_europeia():
    # "Hi-lo": dois tons alternados (lá e ré, intervalo de quarta), 0,55 s cada.
    passo = 0.55
    dur = passo * 4
    t = t_of(dur)
    alto = ((t // passo) % 2).astype(bool)
    f = np.where(alto, 587.0, 440.0)
    # pequeno glissando de 12 ms entre as notas para não estalar
    k = int(0.012 * SR)
    kernel = np.ones(k) / k
    f = np.convolve(np.concatenate([f[-k:], f]), kernel, mode="valid")[: len(t)]
    fase = fase_ciclica(f)
    s = serra_suave(fase, 587) * 0.6 + quadrada_suave(fase, 587) * 0.5
    return finalizar(s, drive=1.6)


def sirene_longa():
    # Sirene de alerta (estilo defesa civil): sobe devagar, sustenta e desce.
    dur = 7.0
    t = t_of(dur)
    x = t / dur
    forma = np.interp(x, [0, 0.36, 0.55, 0.95, 1.0], [0, 1, 1, 0.02, 0])
    forma = 0.5 - 0.5 * np.cos(np.pi * forma)
    f = 320 + (1180 - 320) * forma
    fase = fase_ciclica(f)
    base = quadrada_suave(fase, 1180) * 0.7 + np.sin(fase) * 0.5
    # leve "rotação" de sirene mecânica (modulação de amplitude 9 Hz)
    rot = 0.85 + 0.15 * np.sin(2 * np.pi * 9 * t)
    s = base * rot * (0.35 + 0.65 * forma)
    return finalizar(s, drive=1.5)


def sirene_whoop():
    # "Whoop": sobe 380 → 1250 Hz em 0,5 s, pausa curta, repete.
    sub = 0.5
    pausa = 0.18
    bloco = sub + pausa
    reps = 5
    dur = bloco * reps
    t = t_of(dur)
    tb = t % bloco
    ativo = tb < sub
    x = np.clip(tb / sub, 0, 1)
    f = 380 + (1250 - 380) * (x ** 1.4)
    fase = 2 * np.pi * np.cumsum(f) / SR
    s = quadrada_suave(fase, 1250) * 0.7 + np.sin(fase) * 0.5
    env = np.zeros_like(t)
    for i in range(reps):
        a = int(i * bloco * SR)
        b = a + int(sub * SR)
        env[a:b] = env_ad(b - a, 0.01, 0.03)
    return finalizar(s * env * ativo, drive=1.5)


# ---------------------------------------------------------------- alarmes

def alarme_incendio():
    # Padrão "temporal-3" (ISO 8201): 3 bipes de 0,5 s + pausa de 1,5 s.
    dur = 4.0
    t = t_of(dur)
    f = 2950.0
    fase = 2 * np.pi * f * t
    tom = quadrada_suave(fase, f) * 0.7 + np.sin(fase) * 0.5
    trem = 0.9 + 0.1 * np.sin(2 * np.pi * 14 * t)
    env = np.zeros_like(t)
    for i in range(3):
        a = int(i * 1.0 * SR)
        b = a + int(0.5 * SR)
        env[a:b] = env_ad(b - a, 0.004, 0.008)
    return finalizar(tom * trem * env, drive=1.3)


def despertador_digital():
    # Quatro bipes rápidos e pausa — o clássico despertador de cabeceira.
    dur = 1.1
    t = t_of(dur)
    f = 2048.0
    fase = 2 * np.pi * f * t
    tom = np.sin(fase) + 0.35 * np.sin(2 * fase) + 0.2 * np.sin(3 * fase)
    env = np.zeros_like(t)
    for i in range(4):
        a = int(i * 0.13 * SR)
        b = a + int(0.07 * SR)
        env[a:b] = env_ad(b - a, 0.002, 0.004)
    return finalizar(tom * env, drive=1.2)


def telefone_antigo():
    # Campainha de telefone antigo: dois sinos batidos 20x por segundo.
    dur = 2.4
    t = t_of(dur)
    s = np.zeros_like(t)
    tocando = 1.6
    batida = 1 / 40.0  # alterna os dois sinos a cada 25 ms
    parciais = [(1.0, 1.0), (2.76, 0.55), (5.40, 0.35), (8.93, 0.18)]
    n_bat = int(tocando / batida)
    dec_len = int(0.25 * SR)
    td = np.arange(dec_len) / SR
    sinos = []
    for f0 in (980.0, 1180.0):
        som = np.zeros(dec_len)
        for mult, amp in parciais:
            fr = f0 * mult
            if fr > 15000:
                continue
            som += amp * np.sin(2 * np.pi * fr * td) * np.exp(-td * (9 + 5 * mult))
        sinos.append(som)
    for i in range(n_bat):
        a = int(i * batida * SR)
        som = sinos[i % 2]
        b = min(len(s), a + dec_len)
        s[a:b] += som[: b - a]
    # corta suavemente o fim do toque
    fim = int(tocando * SR)
    rel = int(0.08 * SR)
    s[fim - rel:fim] *= np.linspace(1, 0, rel)
    s[fim:] = 0
    return finalizar(s, drive=1.8)


# ---------------------------------------------------------------- bipes e suaves

def bipe_pager():
    # Três bipes ascendentes, como um pager de plantão.
    dur = 1.5
    t = t_of(dur)
    s = np.zeros_like(t)
    notas = [1318.5, 1661.2, 1975.5]  # mi, sol#, si
    for i, f in enumerate(notas):
        a = int(i * 0.17 * SR)
        n = int(0.12 * SR)
        tt = np.arange(n) / SR
        fase = 2 * np.pi * f * tt
        tom = np.sin(fase) + 0.3 * np.sin(2 * fase) + 0.12 * np.sin(3 * fase)
        s[a:a + n] += tom * env_ad(n, 0.003, 0.01)
    return finalizar(s, drive=1.1)


def carrilhao():
    # Três notas de sino em arpejo (dó, mi, sol), com ressonância longa.
    dur = 2.6
    t = t_of(dur)
    s = np.zeros_like(t)
    notas = [1046.5, 1318.5, 1568.0]
    for i, f in enumerate(notas):
        a = int(i * 0.28 * SR)
        n = len(t) - a
        tt = np.arange(n) / SR
        som = np.zeros(n)
        for mult, amp, dec in [(1, 1.0, 2.2), (2.0, 0.4, 3.5), (3.01, 0.22, 5), (4.2, 0.12, 7)]:
            som += amp * np.sin(2 * np.pi * f * mult * tt) * np.exp(-tt * dec)
        som *= env_ad(n, 0.002, 0.25)
        s[a:] += som
    return finalizar(s, drive=1.1, pico=0.85)


def sonar():
    # Ping de sonar com eco, para quem prefere algo discreto mas inconfundível.
    dur = 2.2
    t = t_of(dur)
    s = np.zeros_like(t)
    f = 1250.0
    for atraso, ganho in [(0.0, 1.0), (0.42, 0.32), (0.84, 0.11)]:
        a = int(atraso * SR)
        n = len(t) - a
        tt = np.arange(n) / SR
        som = np.sin(2 * np.pi * f * tt) * np.exp(-tt * 3.2) + 0.25 * np.sin(4 * np.pi * f * tt) * np.exp(-tt * 6)
        som *= env_ad(n, 0.003, 0.2)
        s[a:] += ganho * som
    return finalizar(s, drive=1.0, pico=0.85)


SONS = {
    "sirene_classica": sirene_classica,
    "sirene_rapida": sirene_rapida,
    "sirene_europeia": sirene_europeia,
    "sirene_longa": sirene_longa,
    "sirene_whoop": sirene_whoop,
    "alarme_incendio": alarme_incendio,
    "despertador_digital": despertador_digital,
    "telefone_antigo": telefone_antigo,
    "bipe_pager": bipe_pager,
    "carrilhao": carrilhao,
    "sonar": sonar,
}

if __name__ == "__main__":
    for nome, fn in SONS.items():
        salvar(nome, fn())


# ================================================================ v3: mais categorias

def sino(f, dur, decaimento=4.0, parciais=((1, 1.0), (2.0, 0.35), (3.0, 0.15), (4.2, 0.08))):
    tt = t_of(dur)
    s = np.zeros_like(tt)
    for mult, amp in parciais:
        if f * mult < 15000:
            s += amp * np.sin(2 * np.pi * f * mult * tt) * np.exp(-tt * decaimento * (0.8 + 0.4 * mult))
    return s * env_ad(len(tt), 0.003, 0.05)


def bipe(f, dur, harm=(1.0, 0.3, 0.12)):
    tt = t_of(dur)
    fase = 2 * np.pi * f * tt
    s = sum(a * np.sin((k + 1) * fase) for k, a in enumerate(harm))
    return s * env_ad(len(tt), 0.003, 0.008)


def colocar(alvo, trecho, inicio_s):
    a = int(inicio_s * SR)
    b = min(len(alvo), a + len(trecho))
    alvo[a:b] += trecho[: b - a]


def chamada_enfermagem():
    # "Dim-dom" de dois tons (mi e dó), estilo chamada de posto de enfermagem.
    dur = 2.4
    s = np.zeros(int(dur * SR))
    colocar(s, sino(659.3, 1.2, 3.0), 0.0)
    colocar(s, sino(523.3, 1.2, 3.0), 0.45)
    return finalizar(s, drive=1.3, pico=0.88)


def monitor_bipe():
    # Bipe curto e regular a cada segundo.
    s = np.zeros(int(1.0 * SR))
    colocar(s, bipe(1000.0, 0.13), 0.0)
    return finalizar(s, drive=1.2)


def eletronico_arpejo():
    s = np.zeros(int(1.4 * SR))
    notas = [1046.5, 1318.5, 1568.0, 2093.0]
    for r in range(2):
        for i, f in enumerate(notas):
            tt = t_of(0.07)
            fase = 2 * np.pi * f * tt
            tom = quadrada_suave(fase, f) * env_ad(len(tt), 0.002, 0.01)
            colocar(s, tom, r * 0.32 + i * 0.075)
    return finalizar(s, drive=1.3)


def eletronico_laser():
    s = np.zeros(int(1.2 * SR))
    for i in range(3):
        tt = t_of(0.16)
        f = 2200 * np.exp(-tt * 11) + 380
        fase = 2 * np.pi * np.cumsum(f) / SR
        tom = (np.sin(fase) + 0.4 * np.sin(2 * fase)) * env_ad(len(tt), 0.002, 0.03)
        colocar(s, tom, i * 0.21)
    return finalizar(s, drive=1.4)


def eletronico_digital():
    s = np.zeros(int(1.5 * SR))
    seq = [1200, 1800, 1500, 1800, 1200, 2400, 1500, 0, 1800, 1200]
    for i, f in enumerate(seq):
        if f == 0:
            continue
        tt = t_of(0.06)
        fase = 2 * np.pi * f * tt
        tom = np.sign(np.sin(fase)) * 0.6 + np.sin(fase) * 0.4
        # "amostragem grosseira" para soar digital
        passo = 6
        tom = np.repeat(tom[::passo], passo)[: len(tt)]
        colocar(s, tom * env_ad(len(tt), 0.001, 0.005), i * 0.075)
    return finalizar(s, drive=1.1)


def progressivo_bipes():
    # Começa com um bipe por segundo e acelera até seis por segundo, subindo o tom.
    dur = 8.0
    s = np.zeros(int(dur * SR))
    t = 0.0
    while t < dur - 0.1:
        x = t / dur
        f = 800 + 900 * x
        colocar(s, bipe(f, 0.09) * (0.45 + 0.55 * x), t)
        t += 1.0 / (1 + 5 * x)
    return finalizar(s, drive=1.2)


def progressivo_sirene():
    # Sirene que começa calma e vai ficando mais rápida e mais forte.
    dur = 9.0
    t = t_of(dur)
    x = t / dur
    velocidade = 0.5 + 2.5 * x ** 1.5
    fase_mod = 2 * np.pi * np.cumsum(velocidade) / SR
    f = 700 + 650 * (0.5 - 0.5 * np.cos(fase_mod))
    fase = fase_ciclica(f)
    s = (quadrada_suave(fase, 1350) * 0.7 + np.sin(fase) * 0.5) * (0.35 + 0.65 * x)
    return finalizar(s, drive=1.4)


def gota():
    s = np.zeros(int(2.0 * SR))
    for i, (f0, g) in enumerate([(1500, 1.0), (1150, 0.7)]):
        tt = t_of(0.25)
        f = f0 * np.exp(-tt * 3) + 300
        fase = 2 * np.pi * np.cumsum(f) / SR
        tom = np.sin(fase) * np.exp(-tt * 14) * env_ad(len(tt), 0.002, 0.02)
        colocar(s, tom * g, i * 0.22)
    return finalizar(s, drive=1.0, pico=0.85)


def curto_ping():
    s = np.zeros(int(0.5 * SR))
    tt = t_of(0.45)
    tom = (np.sin(2 * np.pi * 2093 * tt) + 0.4 * np.sin(2 * np.pi * 3136 * tt)) * np.exp(-tt * 9)
    colocar(s, tom * env_ad(len(tt), 0.002, 0.05), 0.0)
    return finalizar(s, drive=1.0, pico=0.86)


def curto_duplo():
    s = np.zeros(int(0.5 * SR))
    colocar(s, sino(1046.5, 0.2, 9.0), 0.0)
    colocar(s, sino(1568.0, 0.25, 8.0), 0.13)
    return finalizar(s, drive=1.1, pico=0.86)


def curto_atencao():
    s = np.zeros(int(0.6 * SR))
    for i, f in enumerate([880.0, 1108.7, 1318.5]):
        colocar(s, bipe(f, 0.09, harm=(1.0, 0.25, 0.1)), i * 0.11)
    return finalizar(s, drive=1.15)


SONS.update({
    "chamada_enfermagem": chamada_enfermagem,
    "monitor_bipe": monitor_bipe,
    "eletronico_arpejo": eletronico_arpejo,
    "eletronico_laser": eletronico_laser,
    "eletronico_digital": eletronico_digital,
    "progressivo_bipes": progressivo_bipes,
    "progressivo_sirene": progressivo_sirene,
    "gota": gota,
    "curto_ping": curto_ping,
    "curto_duplo": curto_duplo,
    "curto_atencao": curto_atencao,
})

if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[2] == "novos":
    for nome in ["chamada_enfermagem", "monitor_bipe", "eletronico_arpejo", "eletronico_laser", "eletronico_digital",
                 "progressivo_bipes", "progressivo_sirene", "gota", "curto_ping", "curto_duplo", "curto_atencao"]:
        salvar(nome, SONS[nome]())
