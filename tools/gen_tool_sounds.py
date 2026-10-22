"""Synthèse des sons du marteau-piqueur et de la tronçonneuse (aucun échantillon : impulsions, résonances amorties et bruit filtré), encodés en OGG Vorbis mono.

Usage : python tools/gen_tool_sounds.py
Sortie : src/main/resources/assets/minenorth_harvest/sounds/tool/*.ogg
  jackhammer_start / jackhammer_loop / jackhammer_end   (démarrage, boucle de martèlement, retombée)
  chainsaw_start / chainsaw_idle / chainsaw_cut / chainsaw_stop   (lanceur + calages, ralenti 2 temps irrégulier, régime de coupe, arrêt)
Les boucles sont calculées sur un nombre entier de coups et repliées (la fin recouvre le début) : elles bouclent sans claquement.
"""
import pathlib

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44100
OUT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "minenorth_harvest" / "sounds" / "tool"
OUT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(20261022)


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def lowpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def place(buf, snd, at, gain=1.0, wrap=False):
    """Ajoute snd dans buf à l'instant at (secondes) ; wrap = la queue qui dépasse revient au début (boucle sans raccord)."""
    i = int(at * SR)
    n = len(snd)
    if wrap:
        idx = (np.arange(n) + i) % len(buf)
        np.add.at(buf, idx, snd * gain)
    else:
        j = min(len(buf), i + n)
        if j > i:
            buf[i:j] += snd[: j - i] * gain


def normalize(x, peak=0.85):
    x = np.tanh(1.4 * x / (np.max(np.abs(x)) + 1e-9)) / np.tanh(1.4)       # écrêtage doux (compresseur)
    return x * peak


def save(name, x, fade_in=0.0, fade_out=0.0):
    x = np.asarray(x, dtype=np.float64).copy()
    if fade_in:
        n = int(fade_in * SR)
        x[:n] *= np.linspace(0, 1, n)
    if fade_out:
        n = int(fade_out * SR)
        x[-n:] *= np.linspace(1, 0, n)
    x = normalize(x)
    path = OUT / f"{name}.ogg"
    sf.write(str(path), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    back, sr = sf.read(str(path))
    assert sr == SR and abs(len(back) - len(x)) < 2048, (name, len(back), len(x))
    print(f"{name}.ogg  {len(x) / SR:.2f} s  {path.stat().st_size // 1024} Ko")


# ===================================================================== marteau-piqueur
def jack_hit(power=1.0):
    """Un coup de piston : claquement sec + résonance métallique + choc sourd sur la pierre + souffle pneumatique."""
    n = int(0.11 * SR)
    t = np.arange(n) / SR
    click = highpass(rng.standard_normal(n), 2800) * np.exp(-t / 0.0012) * 1.1
    ring = np.zeros(n)
    for f, tau, a in ((1240, 0.012, 0.55), (2310, 0.009, 0.45), (3470, 0.007, 0.35), (5230, 0.005, 0.25), (7800, 0.003, 0.15)):
        ring += a * np.sin(2 * np.pi * f * (1 + rng.normal(0, 0.01)) * t + rng.uniform(0, 6.28)) * np.exp(-t / tau)
    sweep = 105 * np.exp(-t / 0.02) + 52
    thump = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.exp(-t / 0.018) * 1.0
    crunch = bandpass(rng.standard_normal(n), 220, 1100) * np.exp(-t / 0.022) * rng.uniform(0.45, 0.9)   # éclat de pierre
    chuff = bandpass(rng.standard_normal(n), 350, 2600) * (1 - np.exp(-t / 0.004)) * np.exp(-t / 0.03) * 0.35
    return (click * 0.9 + ring * 0.8 + thump * 1.1 * power + crunch * 0.7 + chuff) * (0.8 + 0.2 * power)


def jack_loop(sec=1.2, hz=28.0):
    n = int(sec * SR)
    out = np.zeros(n)
    hits = int(round(sec * hz))
    for k in range(hits):
        at = (k + rng.uniform(-0.07, 0.07)) / hits * sec
        place(out, jack_hit(power=rng.uniform(0.75, 1.0)), at % sec, gain=1.0, wrap=True)
    t = t_axis(sec)
    hiss = bandpass(rng.standard_normal(n), 1800, 7000) * (0.10 + 0.04 * np.sin(2 * np.pi * (1 / sec) * 5 * t))   # souffle d'air continu
    rumble = np.sin(2 * np.pi * (1 / sec) * 40 * t) * 0.12 + np.sin(2 * np.pi * (1 / sec) * 80 * t) * 0.06     # moteur / piston, périodes entières
    return out + hiss + rumble


def jack_start(sec=0.55):
    n = int(sec * SR)
    out = np.zeros(n)
    t = t_axis(sec)
    # claquement de la gâchette puis coups de plus en plus rapides (6 -> 28 Hz)
    place(out, highpass(rng.standard_normal(int(0.02 * SR)), 1500) * np.hanning(int(0.02 * SR)) * 1.2, 0.0)
    at, hz = 0.03, 6.0
    while at < sec - 0.08:
        place(out, jack_hit(power=0.55 + 0.45 * min(1, at / sec)), at, gain=0.6 + 0.4 * min(1, at / sec))
        hz = 6 + 22 * min(1, (at / sec) ** 0.8)
        at += 1.0 / hz * rng.uniform(0.92, 1.08)
    burst = bandpass(rng.standard_normal(n), 1200, 6500) * np.exp(-t / 0.12) * 0.55            # souffle d'air de la mise en pression
    return out + burst


def jack_end(sec=0.62):
    n = int(sec * SR)
    out = np.zeros(n)
    t = t_axis(sec)
    at = 0.0
    while at < sec - 0.12:
        k = at / sec
        place(out, jack_hit(power=max(0.25, 1 - k)), at, gain=max(0.15, 1 - 0.9 * k))
        hz = 28 - 23 * (k ** 0.7)
        at += 1.0 / hz * rng.uniform(0.95, 1.1)
    tail = bandpass(rng.standard_normal(n), 900, 5000) * np.exp(-t / 0.18) * 0.5                # souffle qui retombe
    return out + tail


# ===================================================================== tronçonneuse 2 temps
def saw_fire(freq_body, power=1.0, noise_hi=2500):
    """Une explosion + échappement : pulse grave amorti + craquement filtré (« brap »)."""
    n = int(0.05 * SR)
    t = np.arange(n) / SR
    body = np.zeros(n)
    for h, a in ((1, 1.0), (2, 0.8), (3, 0.6), (4, 0.45), (5, 0.3), (7, 0.2)):
        body += a * np.sin(2 * np.pi * freq_body * h * t + rng.uniform(0, 0.4))
    body *= np.exp(-t / 0.011)
    crack = bandpass(rng.standard_normal(n), 600, noise_hi) * np.exp(-t / 0.006) * 0.8
    return (body * 0.9 + crack) * power


def saw_idle(sec=2.0, hz=24.0):
    n = int(sec * SR)
    out = np.zeros(n)
    slots = int(round(sec * hz))
    for k in range(slots):
        if rng.random() < 0.34:          # raté d'allumage : le « brap-brap … brap » irrégulier du ralenti
            continue
        at = (k + rng.uniform(-0.18, 0.18)) / slots * sec
        place(out, saw_fire(rng.uniform(78, 96), power=rng.uniform(0.55, 1.0)), at % sec, wrap=True)
    t = t_axis(sec)
    chain = bandpass(rng.standard_normal(n), 900, 4200) * 0.05                                  # chaîne qui tourne à vide
    mech = np.sin(2 * np.pi * (1 / sec) * 30 * t) * 0.10                                           # vibration du moteur
    return out + chain + mech


def saw_cut(sec=1.5, hz=132.0):
    n = int(sec * SR)
    out = np.zeros(n)
    pulses = int(round(sec * hz))
    for k in range(pulses):
        at = (k + rng.uniform(-0.04, 0.04)) / pulses * sec
        place(out, saw_fire(rng.uniform(150, 175), power=rng.uniform(0.7, 1.0), noise_hi=4200), at % sec, gain=0.8, wrap=True)
    t = t_axis(sec)
    teeth = np.zeros(n)
    bites = int(sec * 46)
    for k in range(bites):                                      # dents de la chaîne qui mordent dans le bois
        at = (k + rng.uniform(-0.3, 0.3)) / bites * sec
        m = int(0.012 * SR)
        tt = np.arange(m) / SR
        b = bandpass(rng.standard_normal(m), 1200, 6500) * np.exp(-tt / 0.004) * rng.uniform(0.5, 1.0)
        place(teeth, b, at % sec, wrap=True)
    chain = bandpass(rng.standard_normal(n), 2500, 9000) * (0.18 + 0.08 * np.sin(2 * np.pi * (1 / sec) * 132 * t))   # sifflement aigu et agressif
    return out * 0.9 + teeth * 0.9 + chain


def saw_start(sec=1.5):
    n = int(sec * SR)
    out = np.zeros(n)
    t = t_axis(sec)
    # lanceur : cliquetis qui s'accélère
    at = 0.02
    while at < 0.45:
        m = int(0.012 * SR)
        place(out, highpass(rng.standard_normal(m), 2200) * np.hanning(m) * 0.7, at)
        at += 0.012 + 0.018 * (1 - at / 0.45)
    # 2 à 4 allumages ratés puis le moteur s'accroche et monte au ralenti
    at = 0.5
    gap = 0.16
    while at < sec - 0.2:
        place(out, saw_fire(rng.uniform(70, 90), power=rng.uniform(0.7, 1.1)), at)
        gap = max(1 / 24, gap * 0.8)
        at += gap * rng.uniform(0.9, 1.2)
    return out + bandpass(rng.standard_normal(n), 900, 4200) * 0.04 * np.minimum(1, t / 0.9)


def saw_stop(sec=1.0):
    n = int(sec * SR)
    out = np.zeros(n)
    at = 0.0
    gap = 1 / 28
    while at < sec - 0.1:
        k = at / sec
        if rng.random() > 0.15 + 0.6 * k:           # le moteur tousse de plus en plus
            place(out, saw_fire(rng.uniform(70, 90), power=max(0.25, 1 - 0.8 * k)), at, gain=max(0.2, 1 - 0.8 * k))
        gap *= 1.07
        at += gap * rng.uniform(0.85, 1.15)
    place(out, highpass(rng.standard_normal(int(0.05 * SR)), 1800) * np.hanning(int(0.05 * SR)) * 0.4, 0.0)
    return out


if __name__ == "__main__":
    save("jackhammer_start", jack_start(), fade_out=0.03)
    save("jackhammer_loop", jack_loop())
    save("jackhammer_end", jack_end(), fade_out=0.08)
    save("chainsaw_start", saw_start(), fade_out=0.05)
    save("chainsaw_idle", saw_idle())
    save("chainsaw_cut", saw_cut())
    save("chainsaw_stop", saw_stop(), fade_out=0.08)
