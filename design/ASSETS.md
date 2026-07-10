# Respite — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for
> generated hi-res art) and the final file it ships as. **`MISSING`** in the
> source column flags a pixel asset with no `.glyph` source yet — a candidate
> for the glyph pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths
> are under `src/main/resources/` unless noted.

No assets are committed yet; everything below is planned.

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` → README embed, `site/assets/logo.png` — (planned, with first art pass) |
| Mod icon 128×128 | Gemini (prompt in `DESIGN.md` §4) or `/glyph` | `art/icon-128.png` → `assets/respite/icon.png`, store listings — (planned, with first art pass) |
| Lantern glyph 16×16 (Jade/recipe viewers — no HUD slot) | `/glyph` | `art/hud-icon-16.png` (+ `.glyph`) → `assets/respite/textures/gui/glyph.png` — (planned, with Jade integration) |
| Chronometer block textures (side, top, 8 dial faces) | `/glyph` | `art/chronometer-*.glyph` → `assets/respite/textures/block/chronometer_*.png` — (planned, with implementation) |
| Unsteeped Brew item texture | `/glyph` | `art/unsteeped-brew.glyph` → `assets/respite/textures/item/unsteeped_brew.png` — (planned, with implementation) |
| Caffeinated Brew item texture | `/glyph` | `art/caffeinated-brew.glyph` → `assets/respite/textures/item/caffeinated_brew.png` — (planned, with implementation) |
| Weary effect icon | `/glyph` | `art/weary-effect.glyph` → `assets/respite/textures/mob_effect/weary.png` — (planned, with implementation) |
| Time-lapse onset cue | `/sfx` | `art/audio/time-lapse-start.sfx` → `assets/respite/sounds/time_lapse_start.ogg` — (planned, with implementation) |
| Time-lapse settle cue | `/sfx` | `art/audio/time-lapse-end.sfx` → `assets/respite/sounds/time_lapse_end.ogg` — (planned, with implementation) |
| OG image, favicons | Gemini / derived from logo | `site/assets/` — (planned, with site phase) |
