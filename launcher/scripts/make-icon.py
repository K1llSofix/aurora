# Draws the Aurora mark (gradient tile with a four-point spark, as Ui.logo draws it in game)
# into build/icon.png and build/icon.ico for the window, taskbar and installer.
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build')
TEAL = (45, 212, 191)
VIOLET = (139, 92, 246)


def spark(cx, cy, r, waist):
    pts = []
    for i in range(8):
        a = -math.pi / 2 + math.pi / 4 * i
        rad = r if i % 2 == 0 else waist
        pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
    return pts


def mark(size, pad=0.0):
    ss = 4
    s = size * ss
    inset = s * pad
    tile = s - 2 * inset
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    # Diagonal gradient from teal (top left) to violet (bottom right).
    grad = Image.new('RGBA', (s, s))
    px = grad.load()
    for y in range(s):
        for x in range(s):
            t = max(0.0, min(1.0, ((x - inset) + (y - inset)) / (2 * tile)))
            px[x, y] = tuple(int(TEAL[i] + (VIOLET[i] - TEAL[i]) * t) for i in range(3)) + (255,)
    mask = Image.new('L', (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([inset, inset, s - inset, s - inset], radius=tile * 0.3, fill=255)
    img.paste(grad, (0, 0), mask)
    draw = ImageDraw.Draw(img)
    c = s / 2
    draw.polygon(spark(c, c, tile * 0.36, tile * 0.09), fill=(255, 255, 255, 255))
    draw.polygon(spark(c + tile * 0.24, c - tile * 0.24, tile * 0.11, tile * 0.03), fill=(255, 255, 255, 217))
    return img.resize((size, size), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    big = mark(512, pad=0.04)
    big.save(os.path.join(OUT, 'icon.png'))
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    frames = [mark(n, pad=0.02 if n <= 32 else 0.04) for n in sizes]
    frames[-1].save(os.path.join(OUT, 'icon.ico'), format='ICO', sizes=[(n, n) for n in sizes], append_images=frames[:-1])
    print('icon written to', os.path.abspath(OUT))


if __name__ == '__main__':
    sys.exit(main())
