"""Puts the trailer together frame by frame and encodes it with its sound.

The filmed launcher (out/footage) floats as a window over the aurora, the camera glides between
details, captions come in on the beat, the in-game part shows Aurora Client screenshots and the
finale reveals the logo. Writes out/aurora-trailer.mp4 (1920x1080, 60 fps, H.264 + AAC).

    python compose.py                 the whole trailer
    python compose.py --stills 3 10   single frames as out/look/still-*.png, to check quickly
"""
import math
import os
import subprocess
import sys

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import timeline as T

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
W, H, FPS = T.WIDTH, T.HEIGHT, T.FPS
ROOT = os.path.normpath(os.path.join(T.HERE, "..", ".."))
FONTS = os.path.join(ROOT, "src", "main", "resources", "assets", "aurora", "fonts")
LOGO = os.path.join(ROOT, "launcher", "build", "discord-logo.png")
SHOTS_DIR = os.path.join(ROOT, "screenshots")
LOOK = os.path.join(T.OUT, "look")

TEAL = (45, 212, 191)
VIOLET = (139, 92, 246)
MINT = (153, 246, 228)

FOOT_W, FOOT_H, CSS = 1920, 1170, 1.5

FLASHES = [(8.0, 0.7), (16.0, 0.28), (20.0, 0.28), (28.0, 0.28), (36.0, 0.28), (42.0, 0.28), (46.0, 0.6), (56.0, 0.85)]


# ---------------------------------------------------------------- easing


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def smooth(x):
    x = clamp(x)
    return x * x * (3 - 2 * x)


def out_cubic(x):
    x = clamp(x)
    return 1 - (1 - x) ** 3


def out_back(x, s=1.6):
    x = clamp(x)
    return 1 + (s + 1) * (x - 1) ** 3 + s * (x - 1) ** 2


def ramp(t, a, b):
    return clamp((t - a) / (b - a)) if b > a else float(t >= a)


# ---------------------------------------------------------------- fonts and text


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"Inter-{weight}.ttf"), size)


_text_cache = {}


def text_mask(text, fnt, tracking=0.0):
    """White text on black (an L mask), letter-spaced, tightly cropped with a margin for glow."""
    key = (text, fnt.path, fnt.size, round(tracking, 1))
    if key in _text_cache:
        return _text_cache[key]
    ascent, descent = fnt.getmetrics()
    pad = 40
    if tracking == 0:
        width = fnt.getlength(text)
    else:
        width = sum(fnt.getlength(c) for c in text) + tracking * (len(text) - 1)
    img = Image.new("L", (int(math.ceil(width)) + 2 * pad, ascent + descent + 2 * pad), 0)
    d = ImageDraw.Draw(img)
    if tracking == 0:
        d.text((pad, pad), text, font=fnt, fill=255)
    else:
        x = pad
        for c in text:
            d.text((x, pad), c, font=fnt, fill=255)
            x += fnt.getlength(c) + tracking
    _text_cache[key] = img
    return img


def paint(mask, colors, alpha=1.0, vertical=False):
    """Colours a text mask with a gradient (one colour or two)."""
    w, h = mask.size
    if len(colors) == 1:
        layer = Image.new("RGBA", (w, h), colors[0] + (0,))
    else:
        n = h if vertical else w
        ramp_ = np.linspace(0, 1, n)[:, None] if vertical else np.linspace(0, 1, n)[None, :]
        c0, c1 = np.array(colors[0], float), np.array(colors[1], float)
        rgb = (c0 + (c1 - c0) * ramp_[..., None]) * np.ones((h, w, 1))
        layer = Image.fromarray(np.dstack([rgb.astype(np.uint8), np.zeros((h, w), np.uint8)]), "RGBA")
    a = mask if alpha >= 1 else mask.point(lambda v: int(v * alpha))
    layer.putalpha(a)
    return layer


def glow(mask, radius, color, strength):
    blurred = mask.filter(ImageFilter.GaussianBlur(radius))
    return paint(blurred.point(lambda v: min(255, int(v * strength))), [color])


def faded(img, alpha):
    if alpha >= 0.999:
        return img
    out = img.copy()
    out.putalpha(img.getchannel("A").point(lambda v: int(v * alpha)))
    return out


def put(canvas, layer, cx, cy, alpha=1.0, scale=1.0):
    """Composites an RGBA layer centred on (cx, cy)."""
    if alpha <= 0.003:
        return
    if abs(scale - 1) > 1e-3:
        layer = layer.resize((max(1, int(layer.width * scale)), max(1, int(layer.height * scale))), Image.BICUBIC)
    layer = faded(layer, alpha)
    x, y = int(round(cx - layer.width / 2)), int(round(cy - layer.height / 2))
    canvas.alpha_composite(layer, (x, y)) if 0 <= x and 0 <= y and x + layer.width <= W and y + layer.height <= H else paste_clipped(canvas, layer, x, y)


def paste_clipped(canvas, layer, x, y):
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + layer.width), min(H, y + layer.height)
    if x1 <= x0 or y1 <= y0:
        return
    canvas.alpha_composite(layer.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))


# ---------------------------------------------------------------- images


def rounded_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w - 1, h - 1], radius=r, fill=255)
    return m


def shadow_template(w, h, r, spread):
    """A soft shadow for a w x h rounded box, drawn small (a quarter) to stay cheap."""
    q = 4
    pad = spread // q
    m = Image.new("L", (w // q + 2 * pad, h // q + 2 * pad), 0)
    ImageDraw.Draw(m).rounded_rectangle([pad, pad, pad + w // q, pad + h // q], radius=max(1, r // q), fill=200)
    m = m.filter(ImageFilter.GaussianBlur(spread / q / 2.2))
    layer = Image.new("RGBA", m.size, (2, 4, 10, 0))
    layer.putalpha(m)
    return layer, spread


def place(canvas, src, dx, dy, dw, dh, mask=None, resample=Image.BICUBIC):
    """Draws src stretched over the rectangle (dx, dy, dw, dh), only where it is on screen."""
    vx0, vy0, vx1, vy1 = max(0.0, dx), max(0.0, dy), min(float(W), dx + dw), min(float(H), dy + dh)
    if vx1 <= vx0 or vy1 <= vy0:
        return
    ix0, iy0, ix1, iy1 = int(math.floor(vx0)), int(math.floor(vy0)), int(math.ceil(vx1)), int(math.ceil(vy1))
    sx, sy = src.width / dw, src.height / dh
    box = (max(0.0, (ix0 - dx) * sx), max(0.0, (iy0 - dy) * sy), min(src.width, (ix1 - dx) * sx), min(src.height, (iy1 - dy) * sy))
    size = (ix1 - ix0, iy1 - iy0)
    part = src.resize(size, resample, box=box)
    if part.mode != "RGBA":
        part = part.convert("RGBA")
    if mask is not None:
        part.putalpha(mask.resize(size, Image.BILINEAR, box=box))
    canvas.alpha_composite(part, (ix0, iy0))


# ---------------------------------------------------------------- footage


class Reader:
    """Frames of a clip from a given time on, read forward only."""

    def __init__(self, path, start, width, height):
        self.size = (width, height)
        self.bytes = width * height * 3
        self.start = start
        self.proc = subprocess.Popen([FFMPEG, "-loglevel", "error", "-ss", f"{start:.3f}", "-i", path, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                                     stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=self.bytes * 2)
        self.index = -1
        self.frame = None

    def at(self, t):
        want = max(0, int(round((t - self.start) * FPS)))
        while self.index < want:
            raw = self.proc.stdout.read(self.bytes)
            if len(raw) < self.bytes:
                break
            self.frame = raw
            self.index += 1
        return Image.frombuffer("RGB", self.size, self.frame, "raw", "RGB", 0, 1)

    def close(self):
        self.proc.kill()


class Footage:
    """Hands out the launcher frame for any moment of the trailer, reusing readers."""

    def __init__(self):
        self.readers = {}

    def frame(self, clip, segments, u):
        ct = T.clip_time(segments, u)
        seg = next((i for i, (a, b) in enumerate(segments) if a <= ct < b), len(segments) - 1)
        key = (clip, seg)
        reader = self.readers.get(key)
        if reader is None or reader.start > ct or ct - reader.start > 40:
            for k in [k for k in self.readers if k[0] != clip or k[1] < seg]:
                self.readers.pop(k).close()
            reader = Reader(os.path.join(T.FOOTAGE, f"{clip}.mp4"), segments[seg][0], FOOT_W, FOOT_H)
            self.readers[key] = reader
        return reader.at(ct)


# ---------------------------------------------------------------- the pieces


class Trailer:
    def __init__(self):
        self.footage = Footage()
        self.bg = Reader(os.path.join(T.FOOTAGE, "bg.mp4"), 0.0, W, H)
        self.black = Image.new("RGB", (W, H), (3, 4, 9))
        self.window_mask = rounded_mask(FOOT_W, FOOT_H, 30)
        self.shadow, self.shadow_spread = shadow_template(FOOT_W, FOOT_H, 30, 220)
        self.logo = Image.open(LOGO).convert("RGBA")
        self.vignette = self._vignette()
        self.scrim = self._scrim()
        self.captions = {}
        total = sum(1 for shot in T.SHOTS if shot[5])
        for i, shot in enumerate(T.SHOTS):
            if shot[5]:
                self.captions[shot[0]] = self._caption(f"{i:02d} / {total:02d}", shot[5], shot[6])
        self.ingame = [(Image.open(os.path.join(SHOTS_DIR, f)).convert("RGB"), text) for f, text in T.INGAME]
        self.ingame_title = self._caption("IN GAME", "AURORA CLIENT", None)
        self.ingame_lines = [paint(text_mask(text, font("Medium", 30)), [(226, 232, 240)], 0.9) for _, text in T.INGAME]
        self._finale_parts()
        self._discord_parts()

    # -------------------------------------------------- reusable layers

    def _vignette(self):
        y, x = np.mgrid[0:H, 0:W]
        d = np.sqrt(((x - W / 2) / (W / 2)) ** 2 + ((y - H / 2) / (H / 2)) ** 2)
        a = (np.clip((d - 0.75) / 0.75, 0, 1) ** 1.6 * 150).astype(np.uint8)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        layer.putalpha(Image.fromarray(a))
        return layer

    def _scrim(self):
        h = 360
        ramp_ = np.clip(np.linspace(-0.15, 1, h), 0, 1)
        a = (ramp_ ** 1.2 * 225).astype(np.uint8)[:, None] * np.ones((1, W), np.uint8)
        layer = Image.new("RGBA", (W, h), (4, 6, 14, 0))
        layer.putalpha(Image.fromarray(a))
        self.frost_mask = Image.fromarray((np.clip(ramp_ * 1.6, 0, 1) * 255).astype(np.uint8)[:, None] * np.ones((1, W), np.uint8))
        return layer

    def frosted(self, canvas, alpha):
        """Blurs and darkens the bottom of the frame so a caption reads over anything."""
        if alpha <= 0.003:
            return
        h = self.scrim.height
        region = canvas.crop((0, H - h, W, H))
        small = region.resize((W // 6, h // 6), Image.BILINEAR).filter(ImageFilter.GaussianBlur(2.5))
        blurred = small.resize((W, h), Image.BILINEAR)
        mask = self.frost_mask if alpha >= 0.999 else self.frost_mask.point(lambda v: int(v * alpha))
        blurred.putalpha(mask)
        canvas.alpha_composite(blurred, (0, H - h))
        put(canvas, self.scrim, W / 2, H - h / 2, alpha)

    def _caption(self, kicker, title, line):
        k = paint(text_mask(kicker, font("SemiBold", 22), 4), [TEAL])
        t_mask = text_mask(title, font("Bold", 60), 5)
        t = Image.alpha_composite(glow(t_mask, 16, TEAL, 0.55), paint(t_mask, [(255, 255, 255), MINT]))
        l = paint(text_mask(line, font("Medium", 30)), [(226, 232, 240)], 0.9) if line else None
        return k, t, l

    def _finale_parts(self):
        self.word_masks = {}
        self.sub = paint(text_mask("LAUNCHER  ·  CLIENT  ·  MINECRAFT 26.3", font("SemiBold", 26), 9), [(203, 213, 225)], 0.85)
        self.halo = self._halo(900, 520)

    def _halo(self, w, h):
        y, x = np.mgrid[0:h, 0:w]
        d = np.sqrt(((x - w / 2) / (w / 2)) ** 2 + ((y - h / 2) / (h / 2)) ** 2)
        a = np.clip(1 - d, 0, 1) ** 2.2
        rgb = np.zeros((h, w, 3))
        mix = np.clip(x / w, 0, 1)[..., None]
        rgb[:] = np.array(TEAL) * (1 - mix) + np.array(VIOLET) * mix
        img = Image.fromarray(np.dstack([rgb.astype(np.uint8), (a * 120).astype(np.uint8)]), "RGBA")
        return img

    def _discord_parts(self):
        w, h = 560, 176
        card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(card)
        d.rounded_rectangle([0, 0, w - 1, h - 1], radius=16, fill=(17, 18, 23, 246), outline=(255, 255, 255, 26), width=1)
        d.text((22, 18), "PLAYING A GAME", font=font("Bold", 15), fill=(181, 186, 193, 255))
        logo = self.logo.resize((96, 96), Image.LANCZOS)
        card.alpha_composite(logo, (22, 52))
        d.text((136, 50), "Aurora Client", font=font("Bold", 24), fill=(242, 243, 245, 255))
        d.text((136, 84), "Playing on mc.hypixel.net", font=font("Medium", 19), fill=(219, 222, 225, 255))
        d.text((136, 110), "Overworld", font=font("Medium", 19), fill=(219, 222, 225, 255))
        self.discord_card = card

    # -------------------------------------------------- layers per frame

    def background(self, t, darken):
        bg = self.bg.at(min(t, 65.9))
        soft = bg.resize((W // 4, H // 4), Image.BILINEAR).resize((W, H), Image.BILINEAR)
        out = Image.blend(soft, self.black, darken)
        return out.convert("RGBA")

    def window(self, canvas, frame, zoom, fx, fy, alpha=1.0, blur=0.0, punch=1.0):
        s = zoom * punch
        dw, dh = FOOT_W * s, FOOT_H * s
        dx = W / 2 - fx * CSS * s
        dy = H / 2 - fy * CSS * s
        # shadow first
        spread = self.shadow_spread * s
        place(canvas, faded(self.shadow, alpha * 0.9), dx - spread, dy - spread + 40 * s, dw + 2 * spread, dh + 2 * spread, resample=Image.BILINEAR)
        if blur > 0.3:
            small = frame.resize((max(1, int(FOOT_W / (1 + blur))), max(1, int(FOOT_H / (1 + blur)))), Image.BILINEAR)
            frame = small.resize((FOOT_W, FOOT_H), Image.BILINEAR)
        mask = self.window_mask if alpha >= 0.999 else self.window_mask.point(lambda v: int(v * alpha))
        place(canvas, frame, dx, dy, dw, dh, mask=mask)

    def caption(self, canvas, parts, t, start, end):
        k, title, line = parts
        a_in = out_cubic(ramp(t, start + 0.3, start + 0.85))
        a_out = 1 - ramp(t, end - 0.5, end - 0.12)
        a = a_in * a_out
        if a <= 0.003:
            return
        rise = (1 - a_in) * 26
        self.frosted(canvas, a)
        base = H - 150 + rise
        put(canvas, k, W / 2, base - 62, a)
        put(canvas, title, W / 2, base, a)
        if line:
            put(canvas, line, W / 2, base + 58, a * ramp(t, start + 0.5, start + 1.0))

    def flash(self, canvas, t):
        strength = 0.0
        for tc, s in FLASHES:
            if 0 <= t - tc < 0.5:
                strength = max(strength, s * math.exp(-(t - tc) / 0.09))
        if strength > 0.01:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (226, 255, 250, int(255 * strength))))

    def punch(self, t):
        p = 1.0
        for tc, s in FLASHES:
            if 0 <= t - tc < 0.6:
                p = max(p, 1 + 0.035 * s * math.exp(-(t - tc) / 0.13))
        return p

    # -------------------------------------------------- scenes

    def launcher(self, t):
        shot = next(s for s in T.SHOTS if s[1] <= t < s[2])
        name, start, end, clip, segments, title, line, keys = shot
        u = t - start
        z, fx, fy = camera(keys, u)
        canvas = self.background(t, 0.42 if name != "intro" else 0.5)
        frame = self.footage.frame(clip, segments, u)
        alpha, blur, scale = 1.0, 0.0, 1.0
        if name == "intro":
            alpha = smooth(ramp(t, 0.2, 2.2))
            blur = 9 * (1 - out_cubic(ramp(t, 0.2, 2.3)))
            scale = 1 + 0.08 * (1 - out_cubic(ramp(t, 0.2, 3.2)))
        # a small punch on every jump inside a shot
        edge = 0.0
        for a, b in segments[:-1]:
            edge += b - a
            if 0 <= u - edge < 0.4:
                scale *= 1 + 0.02 * math.exp(-(u - edge) / 0.1)
        self.window(canvas, frame, z * scale, fx, fy, alpha=alpha, blur=blur, punch=self.punch(t))
        if name == "intro":
            self.intro_title(canvas, t)
        elif name in self.captions:
            self.caption(canvas, self.captions[name], t, start, end)
        if name == "settings":
            self.discord(canvas, t, start)
        return canvas

    def intro_title(self, canvas, t):
        dim = smooth(ramp(t, 4.9, 5.6)) * (1 - ramp(t, 7.75, 8.0))
        if dim <= 0:
            return
        canvas.alpha_composite(Image.new("RGBA", (W, H), (3, 5, 12, int(170 * dim))))
        a = out_cubic(ramp(t, 5.1, 5.9)) * (1 - ramp(t, 7.7, 7.95))
        grow = 1 + 0.06 * ramp(t, 5.1, 7.95)
        put(canvas, self.halo, W / 2, 470, a * 0.8, grow)
        logo = self.logo.resize((150, 150), Image.LANCZOS)
        put(canvas, logo, W / 2, 400 - (1 - a) * 20, a, out_back(ramp(t, 5.1, 5.8)) * 0.15 + 0.85 * grow)
        word = self.wordmark(96, 22 + 30 * (1 - out_cubic(ramp(t, 5.3, 6.3))))
        put(canvas, word, W / 2, 545, out_cubic(ramp(t, 5.3, 6.0)) * (1 - ramp(t, 7.7, 7.95)), grow)
        sub = paint(text_mask("LAUNCHER", font("SemiBold", 30), 18), [TEAL])
        put(canvas, sub, W / 2, 635, out_cubic(ramp(t, 5.8, 6.4)) * (1 - ramp(t, 7.7, 7.95)), grow)

    def wordmark(self, size, tracking):
        key = (size, round(tracking))
        if key not in self.word_masks:
            m = text_mask("AURORA", font("Bold", size), round(tracking))
            self.word_masks[key] = Image.alpha_composite(glow(m, 22, TEAL, 0.5), paint(m, [(255, 255, 255), MINT], vertical=True))
        return self.word_masks[key]

    def discord(self, canvas, t, start):
        a = out_cubic(ramp(t, start + 0.6, start + 1.2)) * (1 - ramp(t, start + 3.7, start + 3.95))
        if a <= 0:
            return
        card = self.discord_card.copy()
        d = ImageDraw.Draw(card)
        elapsed = int(42 + (t - start))
        d.text((136, 136), f"00:{elapsed:02d} elapsed", font=font("Medium", 18), fill=(148, 163, 184, 255))
        x = W - 330 + (1 - a) * 120
        put(canvas, glow(card.getchannel("A"), 30, VIOLET, 0.35), x, 360, a)
        put(canvas, card, x, 360, a)

    def ingame_scene(self, t):
        canvas = self.background(t, 0.45)
        i = min(len(self.ingame) - 1, int((t - T.INGAME_START) // 2))
        u = t - T.INGAME_START - 2 * i
        img, _ = self.ingame[i]
        width = 1560
        height = width * img.height / img.width
        zoom = 1 + 0.05 * u / 2
        slide = (1 - out_cubic(ramp(u, 0, 0.35))) * 260
        a = ramp(u, 0, 0.12)
        dw, dh = width * zoom, height * zoom
        dx, dy = W / 2 - dw / 2 + slide, H / 2 - dh / 2 - 40
        spread = 160 * zoom
        place(canvas, faded(self.shadow, a), dx - spread, dy - spread + 30, dw + 2 * spread, dh + 2 * spread, resample=Image.BILINEAR)
        mask = rounded_mask(int(img.width), int(img.height), int(22 * img.width / width))
        if a < 1:
            mask = mask.point(lambda v: int(v * a))
        place(canvas, img, dx, dy, dw, dh, mask=mask)
        if 0 <= u < 0.4 and i > 0:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (226, 255, 250, int(255 * 0.25 * math.exp(-u / 0.08)))))
        k, title, _ = self.ingame_title
        a_all = out_cubic(ramp(t, T.INGAME_START + 0.25, T.INGAME_START + 0.8)) * (1 - ramp(t, T.INGAME_END - 0.45, T.INGAME_END - 0.1))
        self.frosted(canvas, a_all)
        put(canvas, k, W / 2, H - 212, a_all)
        put(canvas, title, W / 2, H - 150, a_all)
        line = self.ingame_lines[i]
        put(canvas, line, W / 2 + (1 - out_cubic(ramp(u, 0.05, 0.4))) * 30, H - 92, a_all * out_cubic(ramp(u, 0.05, 0.4)))
        return canvas

    def finale(self, t):
        u = t - T.FINALE_START
        canvas = self.background(t, 0.3)
        a_halo = out_cubic(ramp(u, 0.0, 0.8))
        put(canvas, self.halo, W / 2, 470, a_halo, 1.25 + 0.06 * u / 8)
        size = 250
        s = out_back(ramp(u, 0.02, 0.62)) * 0.4 + 0.6
        logo = self.logo.resize((size, size), Image.LANCZOS)
        put(canvas, glow(logo.getchannel("A"), 30, TEAL, 0.6), W / 2, 400, ramp(u, 0.0, 0.25) * 0.8, s)
        put(canvas, logo, W / 2, 400, ramp(u, 0.0, 0.25), s)
        shine = ramp(u, 2.6, 3.3)
        if 0 < shine < 1:
            band = Image.new("RGBA", (size, size), (255, 255, 255, 0))
            y, x = np.mgrid[0:size, 0:size]
            pos = (shine * 2 - 0.5) * size * 1.6
            alpha = np.clip(1 - np.abs((x + y) - pos) / 60, 0, 1) * 150
            band.putalpha(Image.fromarray((alpha * (np.array(logo.getchannel("A")) / 255)).astype(np.uint8)))
            put(canvas, band, W / 2, 400, 1.0, s)
        tracking = 26 + 46 * (1 - out_cubic(ramp(u, 0.45, 1.6)))
        put(canvas, self.wordmark(120, tracking), W / 2, 640, out_cubic(ramp(u, 0.45, 1.2)))
        put(canvas, self.sub, W / 2, 735 + (1 - out_cubic(ramp(u, 1.5, 2.1))) * 14, out_cubic(ramp(u, 1.5, 2.1)))
        end = smooth(ramp(t, T.DURATION - 1.6, T.DURATION - 0.05))
        if end > 0:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * end))))
        return canvas

    def render(self, t):
        if t < T.INGAME_START:
            canvas = self.launcher(t)
        elif t < T.FINALE_START:
            canvas = self.ingame_scene(t)
        else:
            canvas = self.finale(t)
        canvas.alpha_composite(self.vignette)
        self.flash(canvas, t)
        if t < 0.25:
            canvas.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (1 - t / 0.25)))))
        return canvas.convert("RGB")


def camera(keys, u):
    if u <= keys[0][0]:
        return keys[0][1:]
    for (t0, z0, x0, y0), (t1, z1, x1, y1) in zip(keys, keys[1:]):
        if u <= t1:
            k = smooth((u - t0) / (t1 - t0))
            return z0 + (z1 - z0) * k, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k
    return keys[-1][1:]


# ---------------------------------------------------------------- output


def stills(times):
    os.makedirs(LOOK, exist_ok=True)
    for t in times:
        trailer = Trailer()
        trailer.bg.close()
        trailer.bg = Reader(os.path.join(T.FOOTAGE, "bg.mp4"), max(0.0, t - 0.05), W, H)
        img = trailer.render(t)
        path = os.path.join(LOOK, f"still-{t:05.2f}.png")
        img.save(path)
        print(path)


def full():
    out = os.path.join(T.OUT, "aurora-trailer.mp4")
    audio = os.path.join(T.OUT, "audio", "trailer.wav")
    enc = subprocess.Popen([FFMPEG, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                            "-i", audio, "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p",
                            "-c:a", "aac", "-b:a", "256k", "-movflags", "+faststart", "-shortest", out], stdin=subprocess.PIPE)
    trailer = Trailer()
    frames = int(T.DURATION * FPS)
    for i in range(frames):
        t = i / FPS
        enc.stdin.write(trailer.render(t).tobytes())
        if i % 120 == 0:
            print(f"frame {i}/{frames} ({t:.1f} s)", flush=True)
    enc.stdin.close()
    enc.wait()
    print("written", out)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--stills":
        stills([float(x) for x in sys.argv[2:]])
    else:
        full()
