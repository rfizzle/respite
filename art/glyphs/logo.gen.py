#!/usr/bin/env python3
"""Compose the Respite hero logo as a full-bleed pixel-art PNG.

The suite logo formula (concord design/DESIGN-SYSTEM.md §4): dark stone
brickwork, one central glowing motif in a circular medallion, the mod name in
blocky pixel type below. Everything is computed deterministically — brick
courses, the medallion (shared composition with icon.gen.py), the traced
lantern, and a bitmap pixel font for the wordmark and subtitle — and written
straight to PNG through the vendored glyph.py encoder (the canvas is wide, and
.glyph frames are square by design, so the generator itself is the committed
re-renderable source). Native grid 320×192, shipped at 1280×768 by integer
nearest-neighbor upscale. Light from the upper left; palette per
design/DESIGN.md.
"""
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location(
    "glyph", ROOT / ".ai/skills/mc-textures/scripts/glyph.py")
glyph = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(glyph)

W, H = 320, 192
SCALE_OUT = 4

MCX, MCY = 160.0, 68.0          # medallion centre
R_IN, R_OUT = 46.0, 56.0
FLAME_X, FLAME_Y = MCX, MCY + 9

COL = {
    # background brickwork (a step darker than the medallion field)
    'bg':        '#131938', 'bg_lit': '#1a2250', 'bg_deep': '#0e1330',
    'bg_dk':     '#0b102c', 'bg_dk2': '#080c22',
    'bg_mortar': '#080d24', 'bg_mortar_dk': '#060a1c',
    'star':      '#4a5590', 'star_dim': '#39406e',
    # medallion halo (pre-blended over brick — canvas is opaque)
    'glow1':     '#4d578f', 'glow2': '#3a4478', 'glow3': '#2b3260',
    # indigo stone bezel
    'ink':       '#0a0a0a',
    'bz_sh':     '#252e5e', 'bz_dark': '#3c478f', 'bz_mid': '#5b68c4',
    'bz_lit':    '#7c8ee8', 'bz_spec': '#a6b4ff',
    # medallion brick field
    'br_deep':   '#10163a', 'br': '#1a2250', 'br_lit': '#232e66',
    'mortar':    '#0a0f28', 'vig': '#070b20',
    'br_warm':   '#453a52', 'br_warm2': '#2c2750', 'mortar_w': '#1a1430',
    # lantern
    'fe_sh':     '#2b2f38', 'fe_dark': '#3a3f48', 'fe_mid': '#5f6673',
    'fe_lit':    '#9aa1ad', 'fe_spec': '#c7ccd6',
    'gl_core':   '#fff6dc', 'gl_pale': '#ffe29a', 'gl_mid': '#f2c14e',
    'gl_amber':  '#d18f2f', 'gl_deep': '#a06a1e',
    # wordmark (Moonlight gradient face, extruded)
    'wm_hi':     '#a6b4ff', 'wm_mid': '#7c8ee8', 'wm_low': '#5562b8',
    'wm_ex':     '#232b58', 'wm_glow1': '#39447e', 'wm_glow2': '#262e5c',
    # subtitle (Candleglow face)
    'st_hi':     '#ffe29a', 'st_low': '#f2c14e',
    'st_ex':     '#6e4d16', 'st_glow': '#332c48',
}


def rgba(hexstr):
    hexstr = hexstr.lstrip('#')
    return (int(hexstr[0:2], 16), int(hexstr[2:4], 16), int(hexstr[4:6], 16), 255)


PAL = {k: rgba(v) for k, v in COL.items()}
G = [['bg'] * W for _ in range(H)]

# ---- 1. background brickwork with corner vignette and sparse stars ----------
BRW2, BRH2 = 32, 16
DARKER = {'bg_lit': 'bg', 'bg': 'bg_deep', 'bg_deep': 'bg_dk', 'bg_dk': 'bg_dk2'}
for y in range(H):
    for x in range(W):
        row = y // BRH2
        off = (BRW2 // 2) if (row % 2) else 0
        my = (y % BRH2) < 2
        mx = ((x - off) % BRW2) < 2
        d = max(abs(x - W / 2) / (W / 2), abs(y - H / 2) / (H / 2))
        if my or mx:
            G[y][x] = 'bg_mortar_dk' if d > 0.8 else 'bg_mortar'
            continue
        tone = (row * 3 + (x - off) // BRW2) % 5
        key = 'bg_lit' if tone == 0 else ('bg_deep' if tone == 3 else 'bg')
        if d > 0.78:
            key = DARKER[key]
        if d > 0.96:
            key = DARKER.get(key, key)
        G[y][x] = key

STARS = [(24, 20), (52, 58), (88, 14), (30, 120), (288, 24), (262, 70),
         (300, 120), (36, 164), (292, 168), (18, 84), (302, 46), (70, 108)]
for i, (sx, sy) in enumerate(STARS):
    G[sy][sx] = 'star'
    if i % 4 == 0:  # a few soft crosses, most stay single points
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= sy + dy < H and 0 <= sx + dx < W:
                G[sy + dy][sx + dx] = 'star_dim'


def dist(x, y):
    return math.hypot(x - MCX, y - MCY)


def ang(x, y):
    return math.atan2(y - MCY, x - MCX)


# ---- 2. medallion halo (pre-blended rings over the brick) -------------------
for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# ---- 3. indigo stone bezel annulus ------------------------------------------
for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            shade = math.cos(a - math.radians(225))
            bump = 0.6 * math.sin(a * 8) + 0.4 * math.sin(a * 15 + 1.1)
            base = shade + bump * 0.3
            if d >= R_OUT - 1.2 or d <= R_IN + 1.0:
                G[y][x] = 'ink'
            elif base > 0.85:
                G[y][x] = 'bz_spec'
            elif base > 0.25:
                G[y][x] = 'bz_lit'
            elif base > -0.35:
                G[y][x] = 'bz_mid'
            elif base > -0.8:
                G[y][x] = 'bz_dark'
            else:
                G[y][x] = 'bz_sh'

# ---- 4. medallion brick field, warmed near the flame ------------------------
BRH, BRW = 8, 16
for y in range(H):
    for x in range(W):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (MCY - R_IN)) // BRH)
        off = 4 + ((BRW // 2) if (row % 2) else 0)   # centre corridor kept clear
        my = ((y - (MCY - R_IN)) % BRH) < 1
        mx = ((x - off) % BRW) < 1
        warm = math.hypot(x - FLAME_X, y - FLAME_Y)
        if my or mx:
            G[y][x] = 'mortar_w' if warm < 30 else 'mortar'
        elif warm < 26:
            G[y][x] = 'br_warm'
        elif warm < 34:
            G[y][x] = 'br_warm2'
        else:
            tone = (row * 3 + int((x - off) // BRW)) % 5
            G[y][x] = 'br_lit' if tone == 0 else ('br_deep' if tone == 3 else 'br')
        if d > R_IN - 5 and warm >= 26:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

for y in range(H):
    for x in range(W):
        if R_IN - 1.5 <= dist(x, y) < R_IN:
            G[y][x] = 'ink'

# ---- 5. the hanging lantern (structure shared with icon.gen.py) --------------
LANTERN = [
    ".......KKKK.......",
    "......KIIIIK......",
    ".....KIKKKKIK.....",
    ".....KIK..KIK.....",
    ".....KIK..KIK.....",
    ".....KIK..KIK.....",
    ".....KiK..KiK.....",
    ".....KiKKKKiK.....",
    "......KiiiiK......",
    ".....KiIIIIiK.....",
    "...KKiIIIIIIiKK...",
    ".KDiIIIiiiiIIIiDK.",
    "KKKKKKKKKKKKKKKKKK",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    "..KggDiggggiDggK..",
    ".KKKKKKKKKKKKKKKK.",
    ".KiiiiiiiiiiiiiiK.",
    "KiiiiiiiiiiiiiiiiK",
    "KKKKKKKKKKKKKKKKKK",
]
IRON = {'K': 'ink', 'D': 'fe_dark', 'i': 'fe_mid', 'I': 'fe_lit', 'E': 'fe_spec'}
SC = 2
LW, LH = len(LANTERN[0]) * SC, len(LANTERN) * SC
X0 = int(round(MCX - LW / 2.0))
Y0 = int(round(MCY - LH / 2.0))
S = {}
for ly, lrow in enumerate(LANTERN):
    for lx, ch in enumerate(lrow):
        if ch == '.':
            continue
        for sy in range(SC):
            for sx in range(SC):
                x, y = X0 + lx * SC + sx, Y0 + ly * SC + sy
                if ch == 'g':
                    d = math.hypot((x - FLAME_X), (y - FLAME_Y) / 1.35)
                    S[(x, y)] = ('gl_core' if d < 5 else 'gl_pale' if d < 9
                                 else 'gl_mid' if d < 14 else 'gl_amber' if d < 19
                                 else 'gl_deep')
                else:
                    S[(x, y)] = IRON[ch]
for (x, y), key in list(S.items()):
    if key in ('fe_mid', 'fe_dark'):
        if math.hypot((x - FLAME_X), (y - FLAME_Y) / 1.35) < 11:
            S[(x, y)] = 'fe_lit'
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < W and 0 <= ny < H:
            G[ny][nx] = 'ink'
for (x, y), key in S.items():
    G[y][x] = key

# ---- 6. wordmark & subtitle ---------------------------------------------------
FONT57 = {
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'S': ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    'P': ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    'I': ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
}
FONT35 = {
    'A': ["111", "101", "111", "101", "101"],
    'C': ["111", "100", "100", "100", "111"],
    'E': ["111", "100", "110", "100", "111"],
    'F': ["111", "100", "110", "100", "100"],
    'H': ["101", "101", "111", "101", "101"],
    'I': ["111", "010", "010", "010", "111"],
    'L': ["100", "100", "100", "100", "111"],
    'M': ["10001", "11011", "10101", "10001", "10001"],
    'N': ["1001", "1101", "1011", "1001", "1001"],
    'O': ["111", "101", "101", "101", "111"],
    'R': ["110", "101", "110", "101", "101"],
    'T': ["111", "010", "010", "010", "010"],
    'U': ["101", "101", "101", "101", "111"],
    'V': ["101", "101", "101", "101", "010"],
    'Y': ["101", "101", "010", "010", "010"],
    ' ': ["00", "00", "00", "00", "00"],
}


def text_cells(text, font, scale, gap):
    """Face-pixel set for a text run, plus its total width."""
    cells, cx = set(), 0
    for chn in text:
        rows = font[chn]
        for ry, rrow in enumerate(rows):
            for rx, bit in enumerate(rrow):
                if bit == '1':
                    for sy in range(scale):
                        for sx in range(scale):
                            cells.add((cx + rx * scale + sx, ry * scale + sy))
        cx += len(rows[0]) * scale + gap
    return cells, cx - gap


def emboss(cells, x0, y0, face_of, ex_key, glow1, glow2, ex=2):
    """Paint glow rings, ink outline, extrusion, then the gradient face."""
    face = {(x0 + cx, y0 + cy) for cx, cy in cells}
    extr = {(x + ex, y + ex) for x, y in face} - face
    sil = face | extr
    ring1, ring2 = set(), set()
    for (x, y) in sil:
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                p = (x + dx, y + dy)
                if p in sil:
                    continue
                (ring1 if max(abs(dx), abs(dy)) <= 1 else ring2).add(p)
    ring2 -= ring1
    for x, y in ring2:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = glow2
    for x, y in ring1:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = 'ink'
    if glow1:
        for x, y in ring2:
            if 0 <= x < W and 0 <= y < H and (x + y) % 2 == 0:
                G[y][x] = glow1
    for x, y in extr:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = ex_key
    for x, y in face:
        if 0 <= x < W and 0 <= y < H:
            G[y][x] = face_of(y - y0)
    return sil


WM_SCALE, WM_GAP = 4, 4
wm_cells, wm_w = text_cells("RESPITE", FONT57, WM_SCALE, WM_GAP)
WMX, WMY = (W - wm_w) // 2, 134


def wm_face(row):
    return 'wm_hi' if row < 9 else ('wm_mid' if row < 19 else 'wm_low')


emboss(wm_cells, WMX, WMY, wm_face, 'wm_ex', 'wm_glow1', 'wm_glow2', ex=2)

ST_SCALE, ST_GAP = 2, 2
st_cells, st_w = text_cells("MINECRAFT VITALITY OVERHAUL", FONT35, ST_SCALE, ST_GAP)
STX, STY = (W - st_w) // 2, 170


def st_face(row):
    return 'st_hi' if row < 5 else 'st_low'


emboss(st_cells, STX, STY, st_face, 'st_ex', None, 'st_glow', ex=1)

# ---- 7. upscale and write -----------------------------------------------------
def emit(scale):
    px = []
    for y in range(H):
        for _sy in range(scale):
            row = []
            for x in range(W):
                row.extend([PAL[G[y][x]]] * scale)
            px.extend(row)
    return px


OUT = ROOT / "art/logo.png"
glyph.write_png(OUT, emit(SCALE_OUT), W * SCALE_OUT, H * SCALE_OUT)
print(f"wrote {OUT}  ({W * SCALE_OUT}x{H * SCALE_OUT})")

# OG image (DESIGN-SYSTEM §6): the logo on Ink at 1200×630 — the grid at ×3
# (960×576, integer nearest-neighbor) centered on the ink field.
OGW, OGH = 1200, 630
lw, lh = W * 3, H * 3
ox, oy = (OGW - lw) // 2, (OGH - lh) // 2
ink = rgba(COL['ink'])
og = [ink] * (OGW * OGH)
logo3 = emit(3)
for y in range(lh):
    og[(oy + y) * OGW + ox:(oy + y) * OGW + ox + lw] = logo3[y * lw:(y + 1) * lw]
OG_OUT = ROOT / "site/assets/og-image.png"
OG_OUT.parent.mkdir(parents=True, exist_ok=True)
glyph.write_png(OG_OUT, og, OGW, OGH)
print(f"wrote {OG_OUT}  ({OGW}x{OGH})")
