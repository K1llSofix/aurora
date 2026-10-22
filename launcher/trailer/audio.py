"""The sound of the trailer, synthesised here with numpy (no samples, nothing to license):

- the robotic announcer: a Windows voice slowed into a lower register, blended with a
  zero-phase "robot" copy, ring-modulated, chorused and put in a big hall;
- an original synthwave track at 120 BPM in A minor (pads, bass, arpeggio, lead, drums) that
  follows the edit: a quiet intro under the voice, a drop on the first cut, a break and a
  climax for the in-game part, and a final hit under the logo;
- whooshes on the cuts, impacts on the drops and soft clicks where the cursor clicks.

Writes out/audio/trailer.wav (stereo, 44.1 kHz).
"""
import os
import wave

import numpy as np

import timeline as T

SR = 44100
N = int(T.DURATION * SR)
AUDIO = os.path.join(T.OUT, "audio")
RNG = np.random.default_rng(7)
BEAT = 60 / T.BPM
STEP = BEAT / 4

# ---------------------------------------------------------------- files


def read_wav(path):
    with wave.open(path) as w:
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768
        if w.getnchannels() == 2:
            data = data.reshape(-1, 2).mean(axis=1)
        assert w.getframerate() == SR, "voice must be 44.1 kHz"
    return data


def write_wav(path, stereo):
    pcm = (np.clip(stereo, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ---------------------------------------------------------------- building blocks


def smooth(x, k):
    """Moving average over k samples (a gentle low-pass), same length."""
    if k <= 1:
        return x.copy()
    c = np.cumsum(np.concatenate([np.zeros(1), x]))
    y = (c[k:] - c[:-k]) / k
    return np.concatenate([y, np.full(k - 1, y[-1] if len(y) else 0.0)])


def fft_convolve(x, h):
    n = len(x) + len(h) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(h, size), size)[:n]


def impulse(seconds, decay, predelay=0.025, seed=3):
    """A stereo hall: decorrelated noise dying away, darker as it fades."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = []
    for ch in range(2):
        noise = np.random.default_rng(seed + ch).standard_normal(n)
        dark = smooth(noise, 9) * 2.4
        mix = np.clip(t / decay, 0, 1) * 0.8
        ir = ((1 - mix) * noise + mix * dark) * np.exp(-t * 6.9 / decay)
        ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
        out.append(ir / np.sqrt(np.sum(ir ** 2)))
    return out


def reverb(stereo, ir):
    wet = np.zeros_like(stereo)
    mono = stereo.mean(axis=1)
    for ch in range(2):
        wet[:, ch] = fft_convolve(mono, ir[ch])[: len(stereo)]
    return wet


def bus():
    return np.zeros((N, 2))


def place(target, t, sig, pan=0.0, gain=1.0):
    """Adds a mono signal (or an (n, 2) stereo one) at time t, panned -1..1."""
    i = int(round(t * SR))
    if i >= N or len(sig) == 0:
        return
    if i < 0:
        sig = sig[-i:]
        i = 0
    j = min(N, i + len(sig))
    s = sig[: j - i] * gain
    if s.ndim == 2:
        target[i:j] += s
        return
    target[i:j, 0] += s * np.cos((pan + 1) * np.pi / 4)
    target[i:j, 1] += s * np.sin((pan + 1) * np.pi / 4)


def env(n, a, d, s, r, gate=None):
    """ADSR: attack, decay, sustain level, release; the gate (samples) ends the sustain."""
    gate = n - int(r * SR) if gate is None else gate
    e = np.zeros(n)
    ia, idd = max(1, int(a * SR)), max(1, int(d * SR))
    idx = np.arange(n)
    e = np.where(idx < ia, idx / ia, s + (1 - s) * np.exp(-(idx - ia) / idd * 3))
    rel = idx >= gate
    level = e[min(gate, n - 1)] if gate < n else 0
    e[rel] = level * np.exp(-(idx[rel] - gate) / max(1, int(r * SR)) * 5)
    return e


NOTES = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def hz(note):
    name, octave = note[:-1], int(note[-1])
    midi = 12 * (octave + 1) + NOTES[name]
    return 440.0 * 2 ** ((midi - 69) / 12)


TABLE = 4096
_tables = {}


def table(kind, f, harmonics):
    kmax = max(1, min(harmonics, int(17000 / f)))
    key = (kind, kmax)
    if key not in _tables:
        k = np.arange(1, kmax + 1)
        amps = 1 / k if kind == "saw" else np.where(k % 2 == 1, 1 / k, 0)
        phase = np.arange(TABLE) / TABLE
        wave_ = (np.sin(2 * np.pi * np.outer(k, phase)) * amps[:, None]).sum(axis=0)
        _tables[key] = wave_ / np.max(np.abs(wave_))
    return _tables[key]


def osc(kind, f, n, bright, cents=(0,), vibrato=0.0):
    """Band-limited saw or square, its brightness (0 dark .. 1 bright) may change over time."""
    t = np.arange(n) / SR
    b = np.broadcast_to(np.asarray(bright, dtype=np.float64), (n,))
    dark, light = table(kind, f, 3), table(kind, f, 40)
    out = np.zeros(n)
    for c in cents:
        ff = f * 2 ** (c / 1200)
        inst = ff * (1 + vibrato * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.15) / 0.3, 0, 1))
        ph = (RNG.random() + np.cumsum(inst) / SR) % 1.0
        idx = ph * TABLE
        i0 = idx.astype(np.int64) % TABLE
        frac = idx - np.floor(idx)
        i1 = (i0 + 1) % TABLE
        d = dark[i0] * (1 - frac) + dark[i1] * frac
        l = light[i0] * (1 - frac) + light[i1] * frac
        out += d * (1 - b) + l * b
    return out / len(cents)


def noise(n, seed=None):
    return (np.random.default_rng(seed) if seed is not None else RNG).standard_normal(n)


# ---------------------------------------------------------------- the voice


def robotize(x, size=1024, hop=230):
    """Zero-phase resynthesis: every frame keeps its spectrum but starts in phase, which turns the
    voice into a buzzing machine pitched at the frame rate."""
    win = np.hanning(size)
    out = np.zeros(len(x) + size)
    norm = np.zeros(len(x) + size)
    for i in range(0, len(x) - size, hop):
        mag = np.abs(np.fft.rfft(x[i : i + size] * win))
        frame = np.fft.irfft(mag)
        frame = np.roll(frame, size // 2) * win
        out[i : i + size] += frame
        norm[i : i + size] += win ** 2
    return (out / np.maximum(norm, 1e-3))[: len(x)]


def delayed(x, seconds):
    k = int(seconds * SR)
    return np.concatenate([np.zeros(k), x[: len(x) - k]])


def announcer(stem, hall):
    raw = read_wav(os.path.join(AUDIO, f"raw-{stem}.wav"))
    raw = raw / (np.max(np.abs(raw)) + 1e-9)
    # A lower, slower register: played 14% slower.
    slow = np.interp(np.arange(0, len(raw) - 1, 0.86), np.arange(len(raw)), raw)
    robot = robotize(slow)
    robot /= np.max(np.abs(robot)) + 1e-9
    v = 0.62 * slow + 0.38 * robot
    t = np.arange(len(v)) / SR
    v = 0.78 * v + 0.22 * v * np.sin(2 * np.pi * 92 * t)  # a touch of ring modulation
    v = v + 0.35 * delayed(v, 0.0045)  # metallic comb
    v = smooth(v, 2)
    tail = np.zeros(int(2.8 * SR))
    v = np.concatenate([v, tail])
    stereo = np.stack([v + 0.32 * delayed(v, 0.011), v + 0.32 * delayed(v, 0.017)], axis=1)
    stereo /= np.max(np.abs(stereo)) + 1e-9
    wet = reverb(stereo, hall)
    wet /= np.max(np.abs(wet)) + 1e-9
    return 0.8 * stereo + 0.42 * wet


# ---------------------------------------------------------------- drums and effects


def kick():
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    f = 46 + 120 * np.exp(-t / 0.032)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.17)
    click = noise(n, 11) * np.exp(-t / 0.002) * 0.4
    return np.tanh(1.6 * (body + click))


def clap(seed=21):
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    nz = noise(n, seed)
    band = smooth(nz, 3) - smooth(nz, 26)
    hits = np.zeros(n)
    for k, o in enumerate((0.0, 0.009, 0.018)):
        hits += np.exp(-np.clip(t - o, 0, None) / (0.007 if k < 2 else 0.12)) * (t >= o)
    return band * hits * 1.6


def hat(length=0.045, seed=None):
    n = int((length * 6) * SR)
    t = np.arange(n) / SR
    nz = np.diff(noise(n + 1, seed))
    return nz * np.exp(-t / length) * 0.5


def crash():
    n = int(2.2 * SR)
    t = np.arange(n) / SR
    nz = np.diff(noise(n + 1, 31))
    metal = sum(np.sin(2 * np.pi * f * t) for f in (3170, 4010, 5230, 6870)) * 0.06
    return (nz * 0.5 + metal) * np.exp(-t / 0.9)


def riser(seconds):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    p = t / seconds
    nz = noise(n, 41)
    dark, light = smooth(nz, 40), np.diff(np.concatenate([[0], nz]))
    sweep = (1 - p) * dark * 3 + p * light
    tone = np.sin(2 * np.pi * np.cumsum(220 * (8 ** p)) / SR) * 0.25
    return (sweep * 0.5 + tone) * p ** 2.2


def whoosh(seconds=0.75):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    bell = np.sin(np.pi * t / seconds) ** 2
    nz = noise(n)
    sig = (smooth(nz, 18) * 2 * (1 - bell) + np.diff(np.concatenate([[0], nz])) * bell) * bell
    pan = np.linspace(-0.8, 0.8, n)
    return np.stack([sig * np.cos((pan + 1) * np.pi / 4), sig * np.sin((pan + 1) * np.pi / 4)], axis=1) * 0.55


def impact():
    n = int(2.6 * SR)
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * np.cumsum(38 + 40 * np.exp(-t / 0.08)) / SR) * np.exp(-t / 0.75)
    hit = np.diff(noise(n + 1, 51)) * np.exp(-t / 0.09) * 0.6
    return np.tanh(1.3 * (sub + hit))


def click_sound(key=False):
    n = int(0.03 * SR)
    t = np.arange(n) / SR
    if key:
        return np.sin(2 * np.pi * 2600 * t) * np.exp(-t / 0.004) * 0.6
    return np.sin(2 * np.pi * 1700 * t) * np.exp(-t / 0.008) + np.diff(noise(n + 1, 61)) * np.exp(-t / 0.002) * 0.4


# ---------------------------------------------------------------- the track

CHORDS = [  # A minor: Am, F, C, G, one bar each
    ("A2", ["A3", "C4", "E4"], ["A4", "C5", "E5", "A5"]),
    ("F2", ["F3", "A3", "C4"], ["F4", "A4", "C5", "F5"]),
    ("C3", ["G3", "C4", "E4"], ["C5", "E5", "G5", "C6"]),
    ("G2", ["G3", "B3", "D4"], ["G4", "B4", "D5", "G5"]),
]
MELODY = [  # one phrase per chord: (note, beats)
    [("E5", 1.0), ("D5", 0.5), ("C5", 0.5), ("D5", 1.0), ("E5", 1.0)],
    [("F5", 1.0), ("E5", 0.5), ("D5", 0.5), ("C5", 2.0)],
    [("E5", 1.0), ("G5", 1.0), ("E5", 0.5), ("D5", 0.5), ("C5", 1.0)],
    [("D5", 1.5), ("B4", 0.5), ("G4", 1.0), ("B4", 1.0)],
]


def chord_at(t):
    return CHORDS[int(t // T.BAR) % 4]


def section(t):
    if t < 8:
        return "intro"
    if t < 24:
        return "a"
    if t < 40:
        return "b"
    if t < 46:
        return "break"
    if t < 56:
        return "climax"
    return "finale"


def build():
    drums, bass_, pads, arp, lead, fx, voice = bus(), bus(), bus(), bus(), bus(), bus(), bus()
    send = bus()
    kicks = []

    bars = int(T.DURATION / T.BAR)
    for b in range(bars):
        t0 = b * T.BAR
        sec = section(t0)
        root, pad_notes, arp_notes = chord_at(t0)

        # Under the logo: one long chord that rings out to the end.
        if sec == "finale":
            if abs(t0 - T.FINALE_START) < 1e-6:
                n = int((T.DURATION - t0) * SR)
                br = np.linspace(0.8, 0.12, n)
                e = env(n, 0.01, 1.4, 0.55, 2.2, gate=int((T.DURATION - t0 - 2.4) * SR))
                for note in pad_notes + ["A2", "E5"]:
                    f = hz(note)
                    left = osc("saw", f, n, br, cents=(-11, 5)) * e
                    right = osc("saw", f, n, br, cents=(-5, 12)) * e
                    place(pads, t0, np.stack([left, right], axis=1) * 0.2)
                    place(send, t0, np.stack([left, right], axis=1) * 0.2)
                for k, note in enumerate(["A5", "E5", "C6", "A5", "E6", "C6", "A5", "E5"]):
                    m = int(0.6 * SR)
                    sig = osc("square", hz(note), m, 0.35) * env(m, 0.002, 0.12, 0.0, 0.2)
                    place(arp, t0 + 0.5 + k * BEAT, sig, pan=-0.4 if k % 2 else 0.4, gain=0.06 * (1 - k / 9))
                    place(send, t0 + 0.5 + k * BEAT, sig, gain=0.05)
        # Pads: a wide detuned chord per bar, opening up through the intro.
        if sec != "finale":
            n = int((T.BAR + 0.6) * SR)
            if sec == "intro":
                br = np.linspace(0.06 + 0.1 * b, 0.16 + 0.1 * b, n)
            elif sec == "break":
                br = np.full(n, 0.28)
            else:
                br = np.full(n, 0.5)
            e = env(n, 0.35, 0.8, 0.75, 0.6, gate=int(T.BAR * SR))
            gain = 0.16
            for note in pad_notes:
                f = hz(note)
                left = osc("saw", f, n, br, cents=(-11, 5)) * e
                right = osc("saw", f, n, br, cents=(-5, 12)) * e
                place(pads, t0, np.stack([left, right], axis=1) * gain)
                place(send, t0, np.stack([left, right], axis=1) * gain * 0.6)

        # Drums.
        if sec in ("a", "b", "climax"):
            for beat in range(4):
                t = t0 + beat * BEAT
                place(drums, t, kick(), gain=0.9)
                kicks.append(t)
                if beat in (1, 3):
                    place(drums, t, clap(), gain=0.42, pan=0.05)
                    place(send, t, clap(), gain=0.2)
                place(drums, t + BEAT / 2, hat(), gain=0.32, pan=0.25)
                if sec in ("b", "climax"):
                    for s in (1, 3):
                        place(drums, t + s * STEP, hat(0.02), gain=0.14, pan=-0.3)
            if sec != "a" or b % 4 == 3:
                place(drums, t0 + 3.5 * BEAT, hat(0.22), gain=0.22, pan=0.3)
        if sec == "break" and t0 >= 44:
            # A snare roll speeding up into the climax.
            steps = [i * STEP for i in range(8)] + [1 + i * STEP / 2 for i in range(16)]
            for i, s in enumerate(steps):
                place(drums, t0 + s, clap(seed=21 + i), gain=0.12 + 0.3 * (i / len(steps)), pan=0.05)

        # Bass: eighth notes on the root, with a sub under it.
        if sec in ("a", "b", "climax"):
            for k in range(8):
                t = t0 + k * BEAT / 2
                n = int(0.26 * SR)
                f = hz(root)
                e = env(n, 0.004, 0.12, 0.45, 0.05, gate=int(0.2 * SR))
                sig = (osc("saw", f, n, 0.22) * 0.8 + np.sin(2 * np.pi * f / 2 * np.arange(n) / SR) * 0.7) * e
                place(bass_, t, sig, gain=0.28)

        # Arpeggio: sixteenths over two octaves of the chord, with echoes.
        if sec != "finale" and (sec != "intro" or t0 >= 4):
            pattern = [0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 3, 2, 1, 3, 2]
            sixteenth = sec in ("a", "b", "climax", "break")
            for k in range(16 if sixteenth else 8):
                t = t0 + k * (STEP if sixteenth else BEAT / 2)
                note = arp_notes[pattern[k % 16]]
                n = int(0.22 * SR)
                bright = 0.55 if sec in ("b", "climax") else 0.32
                sig = osc("square", hz(note), n, bright) * env(n, 0.002, 0.07, 0.0, 0.05)
                gain = 0.07 if sec == "intro" else (0.1 if sec != "climax" else 0.12)
                pan = -0.35 if k % 2 == 0 else 0.35
                place(arp, t, sig, pan=pan, gain=gain)
                for e_i, (dt, fb) in enumerate(((0.375, 0.42), (0.75, 0.2))):
                    place(arp, t + dt, sig, pan=-pan, gain=gain * fb)
                place(send, t, sig, gain=gain * 0.5)
                if sec == "climax":
                    up = osc("square", hz(note) * 2, n, 0.4) * env(n, 0.002, 0.05, 0.0, 0.04)
                    place(arp, t + STEP / 2, up, pan=pan * 0.5, gain=0.035)

        # Lead melody in the second groove and the climax.
        if sec in ("b", "climax"):
            phrase = MELODY[b % 4]
            t = t0
            for note, beats in phrase:
                n = int((beats * BEAT + 0.25) * SR)
                e = env(n, 0.02, 0.25, 0.7, 0.18, gate=int(beats * BEAT * SR * 0.92))
                sig = (osc("saw", hz(note), n, 0.6, cents=(-7, 7), vibrato=0.004) * 0.7
                       + osc("square", hz(note), n, 0.5, cents=(0,), vibrato=0.004) * 0.3) * e
                gain = 0.11 if sec == "b" else 0.15
                place(lead, t, sig, gain=gain)
                place(lead, t + 0.375, sig, pan=0.4, gain=gain * 0.25)
                place(send, t, sig, gain=gain * 0.7)
                t += beats * BEAT

    # Effects that follow the edit.
    place(fx, 4.0, riser(4.0), gain=0.32)
    place(fx, 8.0, impact(), gain=0.75)
    place(fx, 8.0, crash(), gain=0.32, pan=0.1)
    place(send, 8.0, impact(), gain=0.25)
    for cut in T.CUTS:
        if cut not in (8.0, 46.0, 56.0):
            place(fx, cut - 0.42, whoosh(), gain=0.5)
    place(fx, 42.0, riser(4.0), gain=0.3)
    place(fx, 46.0, impact(), gain=0.7)
    place(fx, 46.0, crash(), gain=0.34, pan=-0.1)
    place(fx, 55.6, whoosh(0.5), gain=0.4)
    place(fx, 56.0, impact(), gain=0.85)
    place(send, 56.0, impact(), gain=0.35)
    place(fx, 56.0, crash(), gain=0.28)
    for t, kind in T.clicks():
        place(fx, t, click_sound(kind == "key"), gain=0.12 if kind == "key" else 0.2, pan=0.1)

    # Voice lines over everything.
    hall = impulse(3.2, 2.6)
    for stem, start in T.VOICE:
        place(voice, start, announcer(stem, hall), gain=0.95)

    # Pumping: everything melodic ducks on the kick.
    t = np.arange(N) / SR
    pump = np.ones(N)
    for tk in kicks:
        i = int(tk * SR)
        j = min(N, i + int(0.35 * SR))
        seg = t[i:j] - tk
        pump[i:j] = np.minimum(pump[i:j], 1 - 0.55 * np.exp(-seg / 0.1))
    for b_ in (bass_, pads, arp, lead):
        b_ *= pump[:, None]

    # The music steps back while the announcer speaks.
    duck = np.ones(N)
    for stem, start in T.VOICE:
        length = 2.6 if stem == "welcome" else 2.0
        duck = np.minimum(duck, 1 - 0.45 * np.clip(np.minimum((t - start + 0.25) / 0.25, (start + length - t) / 0.5), 0, 1))
    music = drums + bass_ + pads + arp + lead + fx
    music *= duck[:, None]

    hall_music = impulse(2.4, 2.0, seed=9)
    wet = reverb(send * duck[:, None], hall_music)
    mix = music + 0.45 * wet + voice

    # Master: tame the peaks, fade out the tail.
    mix /= np.max(np.abs(mix)) + 1e-9
    mix = np.tanh(1.5 * mix) / np.tanh(1.5)
    fade = np.clip((T.DURATION - t) / 2.5, 0, 1)
    mix *= fade[:, None]
    mix *= 0.89 / (np.max(np.abs(mix)) + 1e-9)
    return mix, {"voice": voice, "music": music}


def main():
    os.makedirs(AUDIO, exist_ok=True)
    mix, parts = build()
    write_wav(os.path.join(AUDIO, "trailer.wav"), mix)
    v = parts["voice"]
    write_wav(os.path.join(AUDIO, "voice-only.wav"), v / (np.max(np.abs(v)) + 1e-9) * 0.9)
    print("written", os.path.join(AUDIO, "trailer.wav"), f"{len(mix) / SR:.1f} s")


if __name__ == "__main__":
    main()
