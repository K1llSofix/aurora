"""A Minecraft player drawn in 3D from a 64x64 skin (classic arms): the boxes of the player model
with the skin's pixels on their faces, the outer layer, a pose, coloured lights and a depth buffer.
Drawn larger and scaled down, so the outline is smooth while the pixels stay sharp.

Model space is in texels with the body centre at the origin: x to the player's left, y down,
z out of the player's chest (towards the camera before turning).
"""
import math

import numpy as np
from PIL import Image

# Texture corner (u, v), size (w, h, d), centre, growth of the outer layer, joint.
PARTS = [
    ((0, 0), (8, 8, 8), (0, -10, 0), 0.0, "head"),
    ((32, 0), (8, 8, 8), (0, -10, 0), 0.5, "head"),
    ((16, 16), (8, 12, 4), (0, 0, 0), 0.0, None),
    ((16, 32), (8, 12, 4), (0, 0, 0), 0.25, None),
    ((40, 16), (4, 12, 4), (-6, 0, 0), 0.0, "rightArm"),
    ((40, 32), (4, 12, 4), (-6, 0, 0), 0.25, "rightArm"),
    ((32, 48), (4, 12, 4), (6, 0, 0), 0.0, "leftArm"),
    ((48, 48), (4, 12, 4), (6, 0, 0), 0.25, "leftArm"),
    ((0, 16), (4, 12, 4), (-2, 12, 0), 0.0, "rightLeg"),
    ((0, 32), (4, 12, 4), (-2, 12, 0), 0.25, "rightLeg"),
    ((16, 48), (4, 12, 4), (2, 12, 0), 0.0, "leftLeg"),
    ((0, 48), (4, 12, 4), (2, 12, 0), 0.25, "leftLeg"),
]
# Where each joint turns: the neck, the shoulders, the hips.
JOINTS = {"head": (0, -6, 0), "rightArm": (-5, -4, 0), "leftArm": (5, -4, 0), "rightLeg": (-2, 6, 0), "leftLeg": (2, 6, 0)}

# Lights in view space: direction towards the light, colour, strength.
LIGHTS = [
    ((-0.55, -0.65, 0.6), (1.0, 0.97, 0.93), 0.6),
    ((0.9, -0.1, 0.35), (0.62, 0.52, 1.0), 0.32),
    ((0.0, -1.0, 0.1), (0.55, 1.0, 0.94), 0.22),
]
AMBIENT = 0.4


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def faces(u, v, w, h, d, grow):
    """The six faces of a box centred at the origin: texture rectangle, the corner where the
    rectangle starts and the edges along its width and height (pointing outwards by a x b)."""
    W, H, D = w + 2 * grow, h + 2 * grow, d + 2 * grow
    x, y, z = W / 2, H / 2, D / 2
    return [
        ((u + d, v + d, w, h), (-x, -y, z), (W, 0, 0), (0, H, 0)),
        ((u + 2 * d + w, v + d, w, h), (x, -y, -z), (-W, 0, 0), (0, H, 0)),
        ((u, v + d, d, h), (-x, -y, -z), (0, 0, D), (0, H, 0)),
        ((u + d + w, v + d, d, h), (x, -y, z), (0, 0, -D), (0, H, 0)),
        ((u + d, v, w, d), (-x, -y, -z), (W, 0, 0), (0, 0, D)),
        ((u + d + w, v, w, d), (-x, y, z), (W, 0, 0), (0, 0, -D)),
    ]


def shade(normal):
    n = normal / np.linalg.norm(normal)
    light = np.full(3, AMBIENT)
    for direction, color, strength in LIGHTS:
        d = np.array(direction) / np.linalg.norm(direction)
        light += np.array(color) * strength * max(0.0, float(n @ d))
    return light


def render(skin, scale, yaw=0.0, pitch=0.0, pose=None, distance=60.0, supersample=3):
    """The player as an RGBA image cropped to its outline, scale pixels per texel at the body centre.
    yaw turns the player (positive: chest towards the right of the picture), pitch tilts the view
    (negative: seen from above); pose maps joints to (x, y, z) turns in degrees."""
    tex = np.asarray(skin.convert("RGBA"), np.float32) / 255
    pose = pose or {}
    view = rot("x", pitch) @ rot("y", yaw)
    quads = []
    for (u, v), (w, h, d), centre, grow, joint in PARTS:
        turn, pivot = np.eye(3), np.zeros(3)
        if joint in pose:
            ax, ay, az = pose[joint]
            turn, pivot = rot("y", ay) @ rot("x", ax) @ rot("z", az), np.array(JOINTS[joint], float)
        for rect, o, a, b in faces(u, v, w, h, d, grow):
            o = pivot + turn @ (np.array(o, float) + centre - pivot)
            o, a, b = view @ o, view @ (turn @ np.array(a, float)), view @ (turn @ np.array(b, float))
            quads.append((rect, o, a, b, grow > 0))

    f = scale * supersample * distance
    cam = np.array([0.0, 0.0, distance])
    corners = np.array([p for _, o, a, b, _ in quads for p in (o, o + a, o + b, o + a + b)])
    sx, sy = f * corners[:, 0] / (distance - corners[:, 2]), f * corners[:, 1] / (distance - corners[:, 2])
    pad = 4 * supersample
    ox, oy = -math.floor(sx.min()) + pad, -math.floor(sy.min()) + pad
    size_w, size_h = math.ceil(sx.max()) + ox + pad, math.ceil(sy.max()) + oy + pad
    color = np.zeros((size_h, size_w, 3), np.float32)
    alpha = np.zeros((size_h, size_w), np.float32)
    depth = np.full((size_h, size_w), np.inf, np.float32)

    for (rx, ry, rw, rh), o, a, b, outer in quads:
        n = np.cross(a, b)
        if n @ (cam - (o + (a + b) / 2)) <= 0:
            continue  # faces away from the camera
        pts = np.array([o, o + a, o + b, o + a + b])
        px_, py_ = f * pts[:, 0] / (distance - pts[:, 2]) + ox, f * pts[:, 1] / (distance - pts[:, 2]) + oy
        x0, x1 = max(0, math.floor(px_.min())), min(size_w, math.ceil(px_.max()) + 1)
        y0, y1 = max(0, math.floor(py_.min())), min(size_h, math.ceil(py_.max()) + 1)
        gy, gx = np.mgrid[y0:y1, x0:x1].astype(np.float32) + 0.5
        ru, rv = (gx - ox) / f, (gy - oy) / f
        denom = n[0] * ru + n[1] * rv - n[2]
        with np.errstate(divide="ignore", invalid="ignore"):
            t = (n @ (o - cam)) / denom
        rel = np.stack([t * ru - o[0], t * rv - o[1], distance - t - o[2]], -1)
        fa, fb = rel @ a / (a @ a), rel @ b / (b @ b)
        inside = (fa >= 0) & (fa < 1) & (fb >= 0) & (fb < 1) & (t > 0)
        tx = rx + np.clip((fa * rw).astype(int), 0, rw - 1)
        ty = ry + np.clip((fb * rh).astype(int), 0, rh - 1)
        texel = tex[ty, tx]
        ok = inside & (t < depth[y0:y1, x0:x1])
        if outer:
            ok &= texel[..., 3] > 0.5
        lit = texel[..., :3] * shade(n)
        color[y0:y1, x0:x1][ok] = lit[ok]
        alpha[y0:y1, x0:x1][ok] = 1.0
        depth[y0:y1, x0:x1][ok] = t[ok]

    rgba = np.dstack([np.clip(color, 0, 1), alpha])
    img = Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA")
    img = img.crop(img.getbbox())
    return img.resize((max(1, round(img.width / supersample)), max(1, round(img.height / supersample))), Image.LANCZOS)


if __name__ == "__main__":
    import os
    import sys

    here = os.path.dirname(os.path.abspath(__file__))
    skin = Image.open(sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "thumbnail-skin.png"))
    pose = {"head": (-4, -14, 0), "rightArm": (28, 0, 6), "leftArm": (-26, 0, -6), "rightLeg": (-20, 0, 0), "leftLeg": (20, 0, 0)}
    out = render(skin, 16, yaw=24, pitch=-12, pose=pose)
    sheet = Image.new("RGBA", (out.width + 40, out.height + 40), (14, 20, 36, 255))
    sheet.alpha_composite(out, (20, 20))
    path = os.path.join(here, "out", "look", "skin3d.png")
    sheet.save(path)
    print(path, out.size)
