'use strict';

// The ffmpeg the trailer scripts use: FFMPEG if set, else the one bundled with Python's
// imageio-ffmpeg, else whatever "ffmpeg" is on PATH.

const { execFileSync } = require('child_process');

let cached = null;

function ffmpegPath() {
  if (cached) return cached;
  if (process.env.FFMPEG) return (cached = process.env.FFMPEG);
  try {
    cached = execFileSync('python', ['-c', 'import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())'], { encoding: 'utf8' }).trim();
  } catch {
    cached = 'ffmpeg';
  }
  return cached;
}

module.exports = { ffmpegPath };
