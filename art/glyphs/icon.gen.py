#!/usr/bin/env python3
"""Compose the Respite hanging-lantern mod icon as a 128px .glyph grid.

The brand motif is the hanging lantern (design/DESIGN.md), set in a circular
stone medallion with a moonlight rim-glow over a midnight-indigo brickwork
field — the suite icon convention (cf. the sibling icon.gen.py generators).
Geometry (true circles, tiling brick, radial glow) is computed mathematically;
the lantern's structure was traced from pixel-art reference renders (semantic
majority-vote downsample) and is embedded below as LANTERN, with its glass
re-lit radially from the flame at compose time. Emitted as an ASCII-grid
.glyph; glyph.py rasterizes it deterministically, so the source re-renders
byte-identically.
"""
import math

N = 128
CX = CY = (N - 1) / 2.0

# ---- palette (Respite moonlight/candleglow over midnight neutrals) ----------
COL = {
    'ink':       '#0a0a0a',
    # moonlight rim glow (alpha falloff)
    'glow1':     '#a6b4ffb0',
    'glow2':     '#7c8ee880',
    'glow3':     '#7c8ee840',
    # indigo stone bezel (lit upper-left) — Moonlight Bright #a6b4ff spec,
    # Moonlight Indigo #7c8ee8 lit face
    'bz_sh':     '#252e5e',
    'bz_dark':   '#3c478f',
    'bz_mid':    '#5b68c4',
    'bz_lit':    '#7c8ee8',
    'bz_spec':   '#a6b4ff',
    # midnight brickwork field
    'br_deep':   '#10163a',
    'br':        '#1a2250',
    'br_lit':    '#232e66',
    'mortar':    '#0a0f28',
    'vig':       '#070b20',     # inner-edge vignette
    # bricks warmed by the lantern glow
    'br_warm':   '#453a52',
    'br_warm2':  '#2c2750',
    'mortar_w':  '#1a1430',
    # lantern ironwork ramp
    'fe_sh':     '#2b2f38',
    'fe_dark':   '#3a3f48',
    'fe_mid':    '#5f6673',
    'fe_lit':    '#9aa1ad',
    'fe_spec':   '#c7ccd6',
    # candleglow ramp — Candleglow Pale #ffe29a, Candleglow #f2c14e
    'gl_core':   '#fff6dc',
    'gl_pale':   '#ffe29a',
    'gl_mid':    '#f2c14e',
    'gl_amber':  '#d18f2f',
    'gl_deep':   '#a06a1e',
}

G = [[None] * N for _ in range(N)]


def dist(x, y):
    return math.hypot(x - CX, y - CY)


def ang(x, y):
    return math.atan2(y - CY, x - CX)


R_IN = 46.0
R_OUT = 56.0

# ---- 1. moonlight glow halo --------------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_OUT < d <= R_OUT + 2:
            G[y][x] = 'glow1'
        elif R_OUT + 2 < d <= R_OUT + 4:
            G[y][x] = 'glow2'
        elif R_OUT + 4 < d <= R_OUT + 6.5:
            G[y][x] = 'glow3'

# ---- 2. indigo stone bezel annulus -------------------------------------------
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN <= d <= R_OUT:
            a = ang(x, y)
            shade = math.cos(a - math.radians(225))          # light from UL
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

# ---- 3. midnight brickwork field, warmed near the flame ----------------------
# flame anchor (matches the lantern blit below)
FLAME_X, FLAME_Y = CX, CY + 9
BRH, BRW = 8, 16
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if d >= R_IN - 1.0:
            continue
        row = int((y - (CY - R_IN)) // BRH)
        # anchor shifted +4 so no vertical joint runs down the icon's center
        # corridor (through the handle hole / above the handle), where a dark
        # line half a pixel off-axis reads as an off-center lantern
        off = 4 + ((BRW // 2) if (row % 2) else 0)
        my = ((y - (CY - R_IN)) % BRH) < 1          # horizontal mortar
        mx = ((x - off) % BRW) < 1                   # vertical mortar
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
        # inner-edge vignette so the lantern pops off the field
        if d > R_IN - 5 and warm >= 26:
            G[y][x] = 'vig' if not (my or mx) else 'mortar'

# inner rim shadow ring (depth under the bezel lip)
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if R_IN - 1.5 <= d < R_IN:
            G[y][x] = 'ink'

# ---- 4. the hanging lantern ---------------------------------------------------
# Structure traced from the pixel-art reference (18×30 logical, majority-vote
# downsample, symmetrized). K outline · E/I/i/D iron ramp · g glass (re-lit
# radially below) · . transparent.
LANTERN = [
    ".......KKKK.......",   # handle top
    "......KIIIIK......",
    ".....KIKKKKIK.....",   # arch shoulders
    ".....KIK..KIK.....",   # arch sides / hole
    ".....KIK..KIK.....",
    ".....KIK..KIK.....",
    ".....KiK..KiK.....",
    ".....KiKKKKiK.....",   # arch meets collar
    "......KiiiiK......",   # collar
    ".....KiIIIIiK.....",   # cap dome
    "...KKiIIIIIIiKK...",
    ".KDiIIIiiiiIIIiDK.",   # flared brim
    "KKKKKKKKKKKKKKKKKK",   # brim underside
    "..KggDiggggiDggK..",   # glass: side panes · posts · centre pane
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
    "..KggDiggggiDggK..",   # glass bottom row
    ".KKKKKKKKKKKKKKKK.",   # base rim
    ".KiiiiiiiiiiiiiiK.",   # base slab
    "KiiiiiiiiiiiiiiiiK",   # flared foot
    "KKKKKKKKKKKKKKKKKK",   # foot outline
]
IRON = {'K': 'ink', 'D': 'fe_dark', 'i': 'fe_mid', 'I': 'fe_lit', 'E': 'fe_spec'}

SCALE = 2
LW, LH = len(LANTERN[0]) * SCALE, len(LANTERN) * SCALE
X0 = int(round(CX - LW / 2.0))
Y0 = int(round(CY - LH / 2.0))

S = {}
for ly, lrow in enumerate(LANTERN):
    for lx, ch in enumerate(lrow):
        if ch == '.':
            continue
        for sy in range(SCALE):
            for sx in range(SCALE):
                x, y = X0 + lx * SCALE + sx, Y0 + ly * SCALE + sy
                if ch == 'g':
                    # radial candleglow from the flame, slightly taller than wide
                    d = math.hypot((x - FLAME_X), (y - FLAME_Y) / 1.35)
                    if d < 5:
                        S[(x, y)] = 'gl_core'
                    elif d < 9:
                        S[(x, y)] = 'gl_pale'
                    elif d < 14:
                        S[(x, y)] = 'gl_mid'
                    elif d < 19:
                        S[(x, y)] = 'gl_amber'
                    else:
                        S[(x, y)] = 'gl_deep'
                else:
                    S[(x, y)] = IRON[ch]

# iron catches the candlelight: warm the inner post edges beside the glass
for (x, y), key in list(S.items()):
    if key in ('fe_mid', 'fe_dark'):
        d = math.hypot((x - FLAME_X), (y - FLAME_Y) / 1.35)
        if d < 11:
            S[(x, y)] = 'fe_lit'

# ---- 5. ink-outline the lantern silhouette, then composite --------------------
for (x, y) in list(S.keys()):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if (nx, ny) not in S and 0 <= nx < N and 0 <= ny < N:
            G[ny][nx] = 'ink'
for (x, y), key in S.items():
    G[y][x] = key

# ---- emit .glyph ---------------------------------------------------------------
pool = "@$%&*+=oOxX0123456789abcdefghijklmnpqrstuvwzABCDEFGHIJKLMNPQRSTUVWZ?!~^"
used = []
for row in G:
    for c in row:
        if c and c not in used:
            used.append(c)
assert len(used) <= len(pool), f"too many colors: {len(used)}"
key2ch = {k: pool[i] for i, k in enumerate(used)}

lines = ["# Respite hanging-lantern mod icon — generated by icon.gen.py",
         f"size: {N}", "", "legend:", "  . transparent"]
for k in used:
    lines.append(f"  {key2ch[k]} {COL[k]}")
lines.append("")
lines.append("frame:")
for row in G:
    lines.append("  " + "".join(key2ch[c] if c else "." for c in row))

OUT = "art/glyphs/icon.glyph"
with open(OUT, "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"wrote {OUT}  ({len(used)} colors)")
