"""The edit of the trailer, shared by the picture (compose.py) and the sound (audio.py).

The music runs at 120 BPM, so a bar is 2 seconds and every cut sits on a bar line.
Times are in seconds of the finished video.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
FOOTAGE = os.path.join(OUT, "footage")

FPS = 60
WIDTH, HEIGHT = 1920, 1080
DURATION = 64.0
BPM = 120
BAR = 4 * 60 / BPM

# Voice lines: (file stem, start time).
VOICE = [("welcome", 1.35), ("aurora", 57.15)]

# Launcher shots: name, start, end, clip, segments of the clip [(from, to), ...] played one after
# another, caption title and line, and camera keys [(time in shot, zoom, focus x, focus y)] in
# CSS pixels of the 1280x780 launcher (zoom 0.78 fits the window into the frame).
FIT = 0.78
SHOTS = [
    ("intro", 0.0, 8.0, "intro", [(0.6, 8.6)], None, None,
     [(0.0, FIT, 640, 390), (1.2, FIT, 640, 390), (3.6, 1.12, 470, 330), (4.6, 1.12, 470, 330), (6.2, FIT * 0.96, 640, 390)]),
    ("play", 8.0, 16.0, "play", [(0.6, 8.6)], "ONE CLICK TO PLAY", "Minecraft, Fabric and Aurora, installed for you",
     [(0.0, FIT, 640, 390), (1.4, FIT, 640, 390), (2.6, 1.08, 930, 700), (6.6, 1.08, 930, 700), (7.8, FIT, 640, 390)]),
    ("servers", 16.0, 20.0, "servers", [(2.4, 6.4)], "EVERY SERVER, LIVE", "Ping, players and a direct join",
     [(0.0, 0.9, 640, 300), (4.0, 0.98, 700, 330)]),
    ("mods", 20.0, 28.0, "mods", [(2.9, 6.9), (7.9, 9.9), (11.6, 13.6)], "MODS · RESOURCE PACKS · SHADERS", "The whole Modrinth catalog, one click away",
     [(0.0, FIT, 640, 390), (1.6, 0.92, 700, 330), (4.0, 0.92, 700, 330), (8.0, FIT, 640, 390)]),
    ("skin", 28.0, 36.0, "skin", [(2.2, 7.7), (11.2, 13.7)], "YOUR SKIN, YOUR STYLE", "Paint it in 3D or pick one from the gallery",
     [(0.0, FIT, 640, 390), (1.6, 1.08, 330, 330), (5.3, 1.08, 330, 330), (5.6, 0.86, 700, 380), (8.0, 0.86, 700, 380)]),
    ("installs", 36.0, 42.0, "installs", [(1.3, 3.3), (6.2, 10.2)], "SHORTCUTS & ICONS", "Straight into the game from your desktop",
     [(0.0, 0.92, 520, 300), (2.0, 0.92, 520, 300), (2.2, 1.0, 470, 420), (6.0, 1.0, 470, 420)]),
    ("settings", 42.0, 46.0, "settings", [(0.3, 4.3)], "DISCORD STATUS", "Your friends see what you play",
     [(0.0, FIT, 640, 390), (4.0, 0.84, 700, 420)]),
]

# In-game screenshots for the Aurora Client montage (file in /screenshots, caption).
INGAME_START, INGAME_END = 46.0, 56.0
INGAME = [
    ("01-main-menu.jpg", "Live animated menus"),
    ("02-mod-menu.jpg", "40+ modules, one menu"),
    ("04-hud-editor.jpg", "Drag-and-snap HUD editor"),
    ("08-target-hud-mob-head.jpg", "Target HUD with a 3D head"),
    ("11-chat-scoreboard-hitboxes.jpg", "Chat, scoreboard, hitboxes"),
]

FINALE_START = 56.0

# Moments that get a whoosh and a flash: every shot change.
CUTS = [8.0, 16.0, 20.0, 28.0, 36.0, 42.0, 46.0, 56.0]


def clip_info(name):
    path = os.path.join(FOOTAGE, f"{name}.json")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def clip_time(segments, t):
    """Where in the clip a moment of the shot (seconds from its start) falls."""
    for a, b in segments:
        if t < b - a:
            return a + t
        t -= b - a
    a, b = segments[-1]
    return b


def clicks():
    """Absolute times of the mouse clicks and key presses seen in the shots, for their sounds."""
    out = []
    for name, start, end, clip, segments, *_ in SHOTS:
        info = clip_info(clip)
        if not info:
            continue
        offset = 0.0
        for a, b in segments:
            for mark in info.get("marks", []):
                if mark["type"] in ("click", "key") and a <= mark["t"] < b:
                    out.append((start + offset + mark["t"] - a, mark["type"]))
            offset += b - a
    return sorted(out)
