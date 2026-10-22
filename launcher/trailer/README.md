# Aurora Launcher trailer

The trailer is built entirely from code: the real launcher is filmed offscreen following a script with a cursor, the voice and music are synthesized, and the frames are edited in Python. The finished video is `out/aurora-trailer.mp4` (1920×1080, 60 fps, H.264 + AAC, 64 seconds).

## What's in it

| Time | What happens |
|---|---|
| 0–8 s | The launcher fades in from a blur, a robotic voice says "Welcome to Aurora", the camera pushes in on "Welcome to Aurora" on the home page, then the AURORA LAUNCHER title card |
| 8–16 s | The drop. "One click to play": a click on "Play", the game downloading, the launch |
| 16–20 s | "Every server, live": live server ping and joining a server |
| 20–28 s | "Mods · Resource packs · Shaders": searching the Modrinth catalog, resource packs, shaders |
| 28–36 s | "Your skin, your style": painting on the 3D skin and the gallery |
| 36–42 s | "Shortcuts & icons": a desktop shortcut and icons from Minecraft textures |
| 42–46 s | "Discord status": the Discord status card |
| 46–56 s | "Aurora Client": a montage of the mod's in-game screenshots |
| 56–64 s | The logo, "AURORA", the voice: "Aurora." |

The music (synthwave, 120 BPM, A minor) is written in code in `audio.py`: every cut lands on a bar line, each with a whoosh and a flash, and the cursor clicks are audible.

## Rebuilding

Requires Node.js (for the launcher), Python 3 with `numpy`, `Pillow` and `imageio-ffmpeg` (which provides ffmpeg), and the Microsoft Zira voice in Windows.

1. Filming the launcher (from the `launcher` folder), about three minutes:

   ```bash
   AURORA_SCRIPT=trailer/director.js AURORA_HIDDEN=1 npx electron .
   ```

   `AURORA_TRAILER_SCENES=play,servers` re-films only the scenes you name. Launching the game is faked during filming: the "Play" buttons show the download and the launch, but Minecraft does not start.

2. The voice:

   ```bash
   powershell -File trailer/voice.ps1 -Text "Welcome to Aurora" -Out trailer/out/audio/raw-welcome.wav
   powershell -File trailer/voice.ps1 -Text "Aurora." -Out trailer/out/audio/raw-aurora.wav
   ```

3. The sound: `python trailer/audio.py` → `out/audio/trailer.wav`.
4. The video: `python trailer/compose.py` → `out/aurora-trailer.mp4`. Single frames to check: `python trailer/compose.py --stills 3.6 12.5`.

The edit lives in `timeline.py`: the timing of each scene, the pieces of footage, the captions and the camera keys (zoom and focus point in the coordinates of the 1280×780 window). The filming script is `director.js`.

## Thumbnail

`python trailer/thumbnail.py` → `out/thumbnail.jpg` (English) and `out/thumbnail-ru.jpg` (Russian), 1920×1080, about 0.5 MB each, fit for YouTube (2 MB limit). The launcher and aurora frames come from the same footage (`out/footage`), and the waving player is drawn in 3D from the skin `thumbnail-skin.png` (`skin3d.py`, classic arms). The layout is set by the constants at the top of `thumbnail.py`: which frames to use, where the windows stand and how they are turned, and the player's pose and size.
