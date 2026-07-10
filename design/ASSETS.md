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
| Full logo | `art/glyphs/logo.gen.py` (writes the PNG directly — wide canvas, so no square `.glyph` intermediate) | `art/logo.png` (master, 1280×768) → README embed, `site/assets/logo.png` (with site phase) |
| Mod icon (128 + 512) | `art/glyphs/icon.gen.py` → `art/glyphs/icon.glyph` (generated 128px grid; 512 via `--scale-to 512`) | `art/icon-128.png`, `art/icon-512.png` (masters) → `assets/respite/icon.png`, store listings (with implementation) |
| Lantern glyph 16×16 (Jade/recipe viewers — no HUD slot) | `art/hud-icon-16.glyph` | `art/hud-icon-16.png` (master) → `assets/respite/textures/gui/glyph.png` (with Jade integration) |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Chronometer block textures (side, top, 8 dial faces) | `/glyph` | `art/chronometer-*.glyph` → `assets/respite/textures/block/chronometer_*.png` — (planned, with implementation) |
| Unsteeped Brew item texture | `/glyph` | `art/unsteeped-brew.glyph` → `assets/respite/textures/item/unsteeped_brew.png` — (planned, with implementation) |
| Caffeinated Brew item texture | `/glyph` | `art/caffeinated-brew.glyph` → `assets/respite/textures/item/caffeinated_brew.png` — (planned, with implementation) |
| Weary effect icon | `/glyph` | `art/weary-effect.glyph` → `assets/respite/textures/mob_effect/weary.png` — (planned, with implementation) |
| Time-lapse onset cue | `/sfx` | `art/audio/time-lapse-start.sfx` → `assets/respite/sounds/time_lapse_start.ogg` — (planned, with implementation) |
| Time-lapse settle cue | `/sfx` | `art/audio/time-lapse-end.sfx` → `assets/respite/sounds/time_lapse_end.ogg` — (planned, with implementation) |
| OG image, favicons | Gemini / derived from logo | `site/assets/` — (planned, with site phase) |
