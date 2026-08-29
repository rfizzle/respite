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
| Lantern glyph 16×16 (Jade/recipe viewers — no HUD slot) | `art/hud-icon-16.glyph` | `art/hud-icon-16.png` (master, not yet shipped — see "Not yet created") |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Chronometer dial faces (8 phases, 32×32) | `art/glyphs/chronometer-dial-{0..7}.glyph` | `assets/respite/textures/block/chronometer_dial_{0..7}.png` |
| Chronometer still face (fixed-time dimensions) | `art/glyphs/chronometer-dial-still.glyph` | `assets/respite/textures/block/chronometer_dial_still.png` |
| Chronometer top/bottom cap | `art/glyphs/chronometer-top.glyph` | `assets/respite/textures/block/chronometer_top.png` |
| Bedroll top face (32×32) | `art/glyphs/bedroll-top.glyph` | `assets/respite/textures/block/bedroll_top.png` |
| Bedroll side (16×16) | `art/glyphs/bedroll-side.glyph` | `assets/respite/textures/block/bedroll_side.png` |
| Bedroll item (32×32) | `art/glyphs/bedroll-item.glyph` | `assets/respite/textures/item/bedroll.png` |
| Unsteeped Brew item (16×16) | `art/glyphs/unsteeped-brew.glyph` | `assets/respite/textures/item/unsteeped_brew.png` |
| Caffeinated Brew item (16×16) | `art/glyphs/caffeinated-brew.glyph` | `assets/respite/textures/item/caffeinated_brew.png` |
| Pocket Chronometer item (32×32) | `art/glyphs/pocket-chronometer.gen.py` → `art/glyphs/pocket-chronometer.glyph` | `assets/respite/textures/item/pocket_chronometer.png` |
| Weary effect icon (18×18) | `art/glyphs/weary-effect.glyph` | `assets/respite/textures/mob_effect/weary.png` |
| Exhausted effect icon (18×18) | `art/glyphs/exhausted-effect.glyph` | `assets/respite/textures/mob_effect/exhausted.png` |
| Well-Rested effect icon (18×18) | `art/glyphs/well-rested-effect.glyph` | `assets/respite/textures/mob_effect/well_rested.png` |

## Audio (.sfx — procedural synthesis)

| Asset | `.sfx` source | Final asset |
|---|---|---|
| Time-lapse onset cue | `art/audio/time-lapse-start.sfx` | `assets/respite/sounds/ui/time_lapse_start.ogg` |
| Time-lapse settle cue | `art/audio/time-lapse-end.sfx` | `assets/respite/sounds/ui/time_lapse_end.ogg` |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Favicons | Derived from logo | `site/assets/` — (planned, with site phase) |
| Lantern glyph, shipped copy | `art/hud-icon-16.png` (master exists) | `assets/respite/textures/gui/glyph.png` — (planned; the Jade/WTHIT providers ship without an icon today) |
