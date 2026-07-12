# Respite — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for
> generated hi-res art) and the final file it ships as. **`MISSING`** in the
> source column flags a pixel asset with no `.glyph` source yet — a candidate
> for the glyph pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths
> are under `src/main/resources/` unless noted.

## Branding masters

| Asset | Source | Final / derived copies |
|---|---|---|
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` (master, 3172×1344) → README embed, `site/assets/logo.png` (1600×678 web copy), `site/assets/og-image.png` (1200×630 on Ink) |
| Mod icon (128 + 512) | `art/glyphs/icon.gen.py` → `art/glyphs/icon.glyph` (generated 128px grid; 512 via `--scale-to 512`) | `art/icon-128.png`, `art/icon-512.png` (masters) → `assets/respite/icon.png` (shipped), `site/assets/icon.png`, store listings |
| Lantern glyph 16×16 (Jade/recipe viewers — no HUD slot) | `art/hud-icon-16.glyph` | `art/hud-icon-16.png` (master) → `assets/respite/textures/gui/glyph.png` (with Jade integration) |

## Block textures

| Asset | Source | Final / derived copies |
|---|---|---|
| Chronometer dial faces (8 phases, 32×32) | `art/glyphs/chronometer-dial-0..7.glyph` | `assets/respite/textures/block/chronometer_dial_0..7.png` |
| Chronometer still face (fixed-time dimensions) | `art/glyphs/chronometer-dial-still.glyph` | `assets/respite/textures/block/chronometer_dial_still.png` |
| Chronometer top/bottom cap | `art/glyphs/chronometer-top.glyph` | `assets/respite/textures/block/chronometer_top.png` |
| Bedroll top face (32×32) | `art/glyphs/bedroll-top.glyph` | `assets/respite/textures/block/bedroll_top.png` |
| Bedroll side (16×16) | `art/glyphs/bedroll-side.glyph` | `assets/respite/textures/block/bedroll_side.png` |

## Item textures

| Asset | Source | Final / derived copies |
|---|---|---|
| Unsteeped Brew item (16×16) | `art/unsteeped-brew.glyph` | `assets/respite/textures/item/unsteeped_brew.png` |
| Caffeinated Brew item (16×16) | `art/caffeinated-brew.glyph` | `assets/respite/textures/item/caffeinated_brew.png` |
| Bedroll item (32×32) | `art/glyphs/bedroll-item.glyph` | `assets/respite/textures/item/bedroll.png` |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Weary effect icon | `/glyph` | `art/weary-effect.glyph` → `assets/respite/textures/mob_effect/weary.png` |
| Exhausted effect icon | `/glyph` | `art/exhausted-effect.glyph` → `assets/respite/textures/mob_effect/exhausted.png` |
| Time-lapse onset cue | `/sfx` | `art/audio/time-lapse-start.sfx` → `assets/respite/sounds/time_lapse_start.ogg` — (planned, with implementation) |
| Time-lapse settle cue | `/sfx` | `art/audio/time-lapse-end.sfx` → `assets/respite/sounds/time_lapse_end.ogg` — (planned, with implementation) |
| OG image, favicons | Gemini / derived from logo | `site/assets/` — (planned, with site phase) |
