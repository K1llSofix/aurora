"""A contact sheet of a clip: python sheet.py clip.mp4 out.png 1.0 2.5 4.0 ... (2 columns, labelled)."""
import subprocess
import sys

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw

clip, out, *times = sys.argv[1:]
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
tiles = []
for t in times:
    raw = subprocess.run([ffmpeg, "-loglevel", "error", "-ss", t, "-i", clip, "-frames:v", "1", "-vf", "scale=640:-2",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    h = len(raw) // (640 * 3)
    im = Image.fromarray(np.frombuffer(raw, np.uint8).reshape(h, 640, 3))
    ImageDraw.Draw(im).text((8, 6), f"{float(t):.2f}s", fill=(255, 255, 0))
    tiles.append(im)
cols = 2
rows = (len(tiles) + 1) // cols
w, h = tiles[0].size
sheet = Image.new("RGB", (w * cols, h * rows))
for i, im in enumerate(tiles):
    sheet.paste(im, ((i % cols) * w, (i // cols) * h))
sheet.save(out)
print(out)
