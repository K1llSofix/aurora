"""The thumbnail of the trailer: the filmed launcher as tilted, glowing windows over the aurora, a
waving player in 3D (the skin from the trailer, thumbnail-skin.png) and the title on the left.
Drawn at twice the size and scaled down, so edges and glows stay smooth.

    python thumbnail.py      out/thumbnail.jpg (English) and out/thumbnail-ru.jpg (Russian), 1920x1080
"""
import math
import os
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import skin3d
import timeline as T
from compose import FFMPEG, FOOT_H, FOOT_W, LOGO, MINT, TEAL, VIOLET, font

K = 2
W, H = 1920 * K, 1080 * K
NAVY = (3, 5, 12)
SKIN = os.path.join(T.HERE, "thumbnail-skin.png")

VERSIONS = {
    "en": {"sub": "LAUNCHER", "badge": "OFFICIAL TRAILER", "file": "thumbnail.jpg"},
    "ru": {"sub": "ЛАУНЧЕР", "badge": "ОФИЦИАЛЬНЫЙ ТРЕЙЛЕР", "file": "thumbnail-ru.jpg"},
}

# Moments of the footage: the aurora behind, the home page in front, the server list behind it.
BG_TIME = 62.0
FRONT = ("intro", 6.8)
BACK = ("servers", 2.0)

# Windows in design pixels (1920x1080): centre, width, turn (yaw, pitch, roll in degrees).
FRONT_AT = dict(cx=1405, cy=560, w=960, yaw=16, pitch=-3, roll=-1.5)
BACK_AT = dict(cx=1650, cy=330, w=840, yaw=20, pitch=-3, roll=-1.5)
DEPTH = 2400

# The player: pixels per texel, where the feet stand, how it is turned and posed.
PLAYER = dict(scale=17.5, x=1050, feet=1050, yaw=22, pitch=-10)
POSE = {"head": (-3, -16, -3), "rightArm": (-20, 0, 150), "leftArm": (12, 0, -6), "rightLeg": (-9, 0, 2), "leftLeg": (9, 0, -2)}

LEFT = 104  # where the title column starts


# ---------------------------------------------------------------- footage


def grab(clip, t, size):
    raw = subprocess.run([FFMPEG, "-loglevel", "error", "-ss", f"{t:.3f}", "-i", os.path.join(T.FOOTAGE, f"{clip}.mp4"),
                          "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    return Image.frombuffer("RGB", size, raw, "raw", "RGB", 0, 1)


# ---------------------------------------------------------------- drawing helpers


def gradient(size, c0, c1, vertical=False):
    w, h = size
    t = np.linspace(0, 1, h if vertical else w)
    t = t[:, None, None] if vertical else t[None, :, None]
    rgb = np.array(c0, float) + (np.array(c1, float) - np.array(c0, float)) * t
    return Image.fromarray(np.broadcast_to(rgb, (h, w, 3)).astype(np.uint8), "RGB")


def colored(mask, fill):
    """An RGBA layer: fill (a colour or an RGB image) shown through mask."""
    layer = (Image.new("RGB", mask.size, fill) if isinstance(fill, tuple) else fill).convert("RGBA")
    layer.putalpha(mask)
    return layer


def scaled(mask, k):
    return mask if k >= 1 else mask.point(lambda v: int(v * k))


def blur(mask, radius):
    """A Gaussian blur, done on a smaller copy for big radii (glows need no detail)."""
    s = max(1, int(radius // 10))
    if s == 1:
        return mask.filter(ImageFilter.GaussianBlur(radius))
    small = mask.resize((max(1, mask.width // s), max(1, mask.height // s)), Image.BILINEAR)
    return small.filter(ImageFilter.GaussianBlur(radius / s)).resize(mask.size, Image.BILINEAR)


def grow(mask, pad):
    """The mask on a bigger transparent sheet, so a blur of it has room to spread."""
    out = Image.new("L", (mask.width + 2 * pad, mask.height + 2 * pad), 0)
    out.paste(mask, (pad, pad))
    return out


def over(canvas, layer, x, y):
    """Alpha-composites layer with its top-left at (x, y), clipped to the canvas."""
    x, y = int(round(x)), int(round(y))
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(canvas.width, x + layer.width), min(canvas.height, y + layer.height)
    if x1 > x0 and y1 > y0:
        canvas.alpha_composite(layer.crop((x0 - x, y0 - y, x1 - x, y1 - y)), (x0, y0))


def light(canvas, layer, x, y, strength=1.0):
    """Screens an RGBA layer onto the canvas: light adds up instead of covering."""
    x, y = int(round(x)), int(round(y))
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(canvas.width, x + layer.width), min(canvas.height, y + layer.height)
    if x1 <= x0 or y1 <= y0:
        return
    part = np.asarray(layer.crop((x0 - x, y0 - y, x1 - x, y1 - y)), np.float32) / 255
    base = np.asarray(canvas.crop((x0, y0, x1, y1)), np.float32) / 255
    add = part[..., :3] * part[..., 3:] * strength
    rgb = 1 - (1 - base[..., :3]) * (1 - add)
    out = np.dstack([rgb, base[..., 3:]])
    canvas.paste(Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA"), (x0, y0))


def text_mask(text, weight, size, tracking=0.0):
    """White text on black at the drawn size, letter-spaced, cropped to the ink with room for glows.
    Returns the mask and the margin around the ink."""
    fnt = font(weight, round(size * K))
    tracking *= K
    ascent, descent = fnt.getmetrics()
    width = sum(fnt.getlength(c) for c in text) + tracking * (len(text) - 1)
    room = round(size * K)
    img = Image.new("L", (math.ceil(width) + 2 * room, ascent + descent + 2 * room), 0)
    d = ImageDraw.Draw(img)
    x = room
    for c in text:
        d.text((x, room), c, font=fnt, fill=255)
        x += fnt.getlength(c) + tracking
    x0, y0, x1, y1 = img.getbbox()
    pad = round(size * K * 0.6)
    return img.crop((x0 - pad, y0 - pad, x1 + pad, y1 + pad)), pad


def text_to_width(text, weight, size, width):
    """Like text_mask, letter-spaced so the ink is exactly width (supersampled pixels) wide."""
    mask, pad = text_mask(text, weight, size)
    tracking = (width - (mask.width - 2 * pad)) / (K * (len(text) - 1))
    return text_mask(text, weight, size, tracking)


# ---------------------------------------------------------------- windows


def project(w, h, cx, cy, yaw, pitch, roll):
    """Screen corners (tl, tr, br, bl) of a w x h card centred on (cx, cy), turned by the angles."""
    a, b, c = (math.radians(v) for v in (yaw, pitch, roll))
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    r = ry @ rx @ rz
    depth = DEPTH * K
    out = []
    for x, y in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        X, Y, Z = r @ np.array([x, y, 0.0])
        out.append((cx + depth * X / (depth + Z), cy + depth * Y / (depth + Z)))
    return out


def perspective_coeffs(dst, src):
    """Coefficients for Image.transform(PERSPECTIVE) that map the quad dst (output) onto src (input)."""
    rows, rhs = [], []
    for (x, y), (u, v) in zip(dst, src):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        rhs += [u, v]
    return np.linalg.solve(np.array(rows, float), np.array(rhs, float)).tolist()


MARGIN = 6


def window_face(frame, dim=1.0):
    """The launcher frame as a window: rounded corners, a light edge, a faint glass sheen, and a transparent
    margin repeating the edge colours so the tilted edges stay clean."""
    w, h = frame.size
    rgb = np.asarray(frame, np.float32)
    # a soft sheen falling from the top left corner
    y, x = np.mgrid[0:h, 0:w]
    t = x / w * 0.65 + y / h * 0.35
    rgb = rgb + (255 - rgb) * (np.clip(1 - t / 0.5, 0, 1) ** 2 * 0.07)[..., None]
    rgb *= dim
    face = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
    edge = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle([1, 1, w - 2, h - 2], radius=30, outline=(255, 255, 255, int(58 * dim)), width=3)
    face.alpha_composite(edge)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=30, fill=255)
    padded = np.pad(np.asarray(face)[..., :3], ((MARGIN, MARGIN), (MARGIN, MARGIN), (0, 0)), mode="edge")
    alpha = np.pad(np.asarray(mask), MARGIN)
    return Image.fromarray(np.dstack([padded, alpha]), "RGBA")


def warp(face, corners):
    """Draws the window face onto the quad; returns the layer and its top-left corner."""
    xs, ys = [p[0] for p in corners], [p[1] for p in corners]
    x0, y0 = math.floor(min(xs)) - 4, math.floor(min(ys)) - 4
    x1, y1 = math.ceil(max(xs)) + 4, math.ceil(max(ys)) + 4
    m, sw, sh = MARGIN, face.width - 2 * MARGIN, face.height - 2 * MARGIN
    src = [(m, m), (m + sw, m), (m + sw, m + sh), (m, m + sh)]
    coeffs = perspective_coeffs([(x - x0, y - y0) for x, y in corners], src)
    return face.transform((x1 - x0, y1 - y0), Image.PERSPECTIVE, coeffs, Image.BICUBIC), x0, y0


def lift(canvas, layer, x, y, glow=1.0, shadow=0.85, drop=38):
    """Puts a layer down floating: a soft shadow below it, coloured light around its edges."""
    pad = 200 * K
    room = grow(layer.getchannel("A"), pad)
    over(canvas, colored(scaled(blur(room, 46 * K), shadow), (1, 2, 6)), x - pad, y - pad + drop * K)
    tint = gradient(room.size, TEAL, VIOLET)
    light(canvas, colored(blur(room, 120 * K), tint), x - pad, y - pad, 0.42 * glow)
    light(canvas, colored(blur(room, 34 * K), tint), x - pad, y - pad, 0.55 * glow)
    over(canvas, layer, x, y)


def window(canvas, frame, at, dim=1.0, glow=1.0, soft=0.0):
    w = at["w"] * K
    h = w * FOOT_H / FOOT_W
    corners = project(w, h, at["cx"] * K, at["cy"] * K, at["yaw"], at["pitch"], at["roll"])
    layer, x, y = warp(window_face(frame, dim), corners)
    if soft:
        layer = layer.filter(ImageFilter.GaussianBlur(soft * K))
    lift(canvas, layer, x, y, glow)


# ---------------------------------------------------------------- the player


def rim(alpha, dx, dy, width):
    """The edge of a silhouette on the side (dx, dy) points to, as a soft line."""
    shifted = Image.new("L", alpha.size, 0)
    shifted.paste(alpha, (-dx, -dy))
    edge = np.clip(np.asarray(alpha, np.int16) - np.asarray(shifted, np.int16), 0, 255).astype(np.uint8)
    return Image.fromarray(edge).filter(ImageFilter.GaussianBlur(width))


def player(canvas):
    p = PLAYER
    figure = skin3d.render(Image.open(SKIN), p["scale"] * K, yaw=p["yaw"], pitch=p["pitch"], pose=POSE)
    x, y = p["x"] * K - figure.width / 2, p["feet"] * K - figure.height
    alpha = figure.getchannel("A")
    lift(canvas, figure, x, y, glow=0.9, shadow=0.7, drop=26)
    # light catching the edges: the window's violet on the right, the sky's mint on the left
    light(canvas, colored(rim(alpha, 7 * K, 0, 2 * K), (170, 140, 255)), x, y, 0.75)
    light(canvas, colored(rim(alpha, -6 * K, -3 * K, 2 * K), MINT), x, y, 0.55)


# ---------------------------------------------------------------- the picture


def backdrop():
    bg = grab("bg", BG_TIME, (1920, 1080)).resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(3 * K))
    canvas = bg.convert("RGBA")
    # darker towards the left, where the title sits
    y, x = np.mgrid[0:H, 0:W]
    shade = np.clip(1 - x / (W * 0.62), 0, 1) ** 1.4 * 0.62
    over(canvas, colored(Image.fromarray((shade * 255).astype(np.uint8)), NAVY), 0, 0)
    # and a soft pool of shade right behind the title
    d = np.sqrt(((x - 500 * K) / (620 * K)) ** 2 + ((y - 540 * K) / (420 * K)) ** 2)
    pool = np.clip(1 - d, 0, 1) ** 1.5 * 0.55
    over(canvas, colored(Image.fromarray((pool * 255).astype(np.uint8)), NAVY), 0, 0)
    return canvas


def vignette(canvas):
    y, x = np.mgrid[0:H, 0:W]
    d = np.sqrt(((x - W / 2) / (W / 2)) ** 2 + ((y - H / 2) / (H / 2)) ** 2)
    a = np.clip((d - 0.7) / 0.7, 0, 1) ** 1.5 * 0.7
    over(canvas, colored(Image.fromarray((a * 255).astype(np.uint8)), (0, 0, 0)), 0, 0)


def sparkle(canvas, cx, cy, r, strength=1.0):
    """A four-pointed star like the one in the logo, with a glow."""
    r = int(r * K)
    n = 4 * r
    y, x = (np.mgrid[-n:n + 1, -n:n + 1]) / r
    d = np.sqrt(np.abs(x)) + np.sqrt(np.abs(y))
    core = np.clip((1 - d) * r * 0.6, 0, 1)
    mask = Image.fromarray((core * 255 * strength).astype(np.uint8))
    light(canvas, colored(blur(mask, r * 0.9), MINT), cx * K - n, cy * K - n, 0.9)
    light(canvas, colored(mask, (240, 255, 252)), cx * K - n, cy * K - n, 1.0)


def scene():
    canvas = backdrop()
    window(canvas, grab(*BACK, (FOOT_W, FOOT_H)), BACK_AT, dim=0.55, glow=0.55, soft=1.2)
    window(canvas, grab(*FRONT, (FOOT_W, FOOT_H)), FRONT_AT)
    player(canvas)
    return canvas


def title(canvas, words):
    # logo
    size = 140
    logo = Image.open(LOGO).convert("RGBA").resize((size * K, size * K), Image.LANCZOS)
    lx, ly = LEFT - 7, 222
    room = grow(logo.getchannel("A"), 80 * K)
    light(canvas, colored(blur(room, 34 * K), TEAL), lx * K - 80 * K, ly * K - 80 * K, 0.7)
    over(canvas, logo, lx * K, ly * K)

    # AURORA
    top = 408
    mask, pad = text_mask("AURORA", "Bold", 160, 7)
    x, y = LEFT * K - pad, top * K - pad
    over(canvas, colored(scaled(blur(mask, 16 * K), 0.7), NAVY), x, y + 10 * K)
    light(canvas, colored(blur(mask, 30 * K), TEAL), x, y, 0.75)
    over(canvas, colored(mask, gradient(mask.size, (255, 255, 255), MINT, vertical=True)), x, y)
    word_w = mask.width - 2 * pad
    word_bottom = top + (mask.height - 2 * pad) / K

    # LAUNCHER, as wide as the word above; a short word gets bigger letters, not wider gaps
    natural, pad = text_mask(words["sub"], "SemiBold", 70)
    per_size = (natural.width - 2 * pad) / 70
    size = max(70, word_w / (per_size + 0.6 * K * (len(words["sub"]) - 1)))
    mask, pad = text_to_width(words["sub"], "SemiBold", size, word_w)
    sx, sy = LEFT * K - pad, (word_bottom + 36) * K - pad
    over(canvas, colored(scaled(blur(mask, 10 * K), 0.6), NAVY), sx, sy + 6 * K)
    light(canvas, colored(blur(mask, 18 * K), TEAL), sx, sy, 0.35)
    over(canvas, colored(mask, gradient(mask.size, TEAL, (150, 120, 250))), sx, sy)
    sub_bottom = word_bottom + 36 + (mask.height - 2 * pad) / K

    # badge
    tmask, tpad = text_mask(words["badge"], "Bold", 29, 4.5)
    tw, th = tmask.width - 2 * tpad, tmask.height - 2 * tpad
    bh = 72 * K
    icon = 25 * K
    bw = 32 * K + icon + 21 * K + tw + 36 * K
    bx, by = LEFT * K, (sub_bottom + 56) * K
    pill = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(pill).rounded_rectangle([0, 0, bw - 1, bh - 1], radius=bh // 2, fill=255)
    room = grow(pill, 120 * K)
    light(canvas, colored(blur(room, 40 * K), gradient(room.size, TEAL, VIOLET)), bx - 120 * K, by - 120 * K, 0.55)
    over(canvas, colored(pill, gradient(pill.size, (20, 184, 166), (124, 58, 237))), bx, by)
    # a thin light rim on the pill
    edge = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(edge).rounded_rectangle([0, 0, bw - 1, bh - 1], radius=bh // 2, outline=70, width=2 * K)
    over(canvas, colored(edge, (255, 255, 255)), bx, by)
    # play triangle
    tri = Image.new("L", (icon, icon), 0)
    ImageDraw.Draw(tri).polygon([(icon * 0.12, 0), (icon, icon / 2), (icon * 0.12, icon)], fill=255)
    over(canvas, colored(tri, (255, 255, 255)), bx + 32 * K, by + (bh - icon) // 2)
    over(canvas, colored(tmask, (255, 255, 255)), bx + 32 * K + icon + 21 * K - tpad, by + (bh - th) // 2 - tpad)

    # a few stars around the title
    sparkle(canvas, LEFT + 176, 246, 9, 0.85)
    sparkle(canvas, LEFT + word_w / K - 30, top - 40, 13)
    sparkle(canvas, LEFT + bw / K + 40, (sub_bottom + 92), 7, 0.7)
    return canvas


def finish(canvas, path):
    img = canvas.convert("RGB").resize((1920, 1080), Image.LANCZOS)
    rgb = np.asarray(img, np.float32)
    rgb += np.random.default_rng(7).normal(0, 1.6, rgb.shape[:2])[..., None]  # grain, against banding
    Image.fromarray(np.clip(rgb + 0.5, 0, 255).astype(np.uint8)).save(path, quality=94, subsampling=0, optimize=True)
    print(path, f"{os.path.getsize(path) / 1024:.0f} KB")


def main():
    base = scene()
    for words in VERSIONS.values():
        canvas = base.copy()
        title(canvas, words)
        vignette(canvas)
        finish(canvas, os.path.join(T.OUT, words["file"]))


if __name__ == "__main__":
    main()
