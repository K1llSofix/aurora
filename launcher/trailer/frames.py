"""Pulls single frames out of a clip as PNGs, to look at footage: python frames.py clip.mp4 out_prefix 0.5 2.0 4.0"""
import subprocess
import sys

import imageio_ffmpeg

clip, prefix, *times = sys.argv[1:]
for t in times:
    out = f"{prefix}-{float(t):05.2f}.png"
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-ss", t, "-i", clip, "-frames:v", "1", out], check=True)
    print(out)
