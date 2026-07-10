# Respite — Design Specification

> Vitality overhaul (sleep, rest & the passage of night) for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Respite makes the night a lived part of survival instead of a skip button: sleep accelerates time visibly, a hearty supper heals overnight, sleeplessness wears you down, and the clock becomes something you can build with. The name evokes rest earned and taken — a pause that restores rather than an absence of play. The visual language draws from **lantern-light against deep night**: hanging lanterns, moonlit indigo skies, warm candleglow spilling onto stone, the quiet hours when the world keeps working. The mythic register is the **night watch** — the one warm light kept burning while everyone else sleeps.

### Tagline

*"Make the night count."*

### Motif

The single motif object is a **hanging lantern** — warm light held against the dark. It may appear in the logo, site headers, and flavor art, and never in another mod's assets. Deliberate distances inside the register: no crescent moons or constellation imagery (Meridian's astral-cartography register owns the mapped sky), and no hourglass (Tribulation's mortality register owns it). Respite's night is felt in lantern-light and sky color, not in star-charts or timers.

### Logo Description

**Full Logo (`art/logo.png`):** Pixel art per the suite formula — dark stone brickwork, one central glowing motif in a circular medallion, mod name in blocky pixel type below. A full-bleed midnight brickwork wall (corner-vignetted, a handful of dim single-pixel stars scattered sparsely — never arranged into patterns or joined by lines) carries the icon's medallion at center-top: the indigo stone bezel with its Moonlight rim-glow around the hanging lantern. Below, "RESPITE" in beveled blocky pixel type carrying the Moonlight gradient (`#A6B4FF` → `#7C8EE8` → `#5562B8`) over an ink outline and soft glow, with the subtitle "MINECRAFT VITALITY OVERHAUL" in Candleglow. Composed deterministically by `art/glyphs/logo.gen.py` at 320×192 native, shipped at 1280×768 by integer upscale.

**Icon (`art/icon-128.png`, `art/icon-512.png`):** The suite medallion — a circular indigo stone bezel lit from the upper left with a soft Moonlight rim-glow, around a midnight-indigo brickwork field. Centered inside, the hanging lantern: arched iron handle, flared cap brim, three glass panes between iron posts glowing radially from a warm flame core, flared foot slab — the nearest bricks warmed by the light. Composed programmatically by `art/glyphs/icon.gen.py` (true circles, tiling brick, radial glow; lantern structure traced from pixel-art reference renders) into `art/glyphs/icon.glyph`, rendered at 128 native and 512 by integer upscale.

**16×16 glyph (`art/hud-icon-16.png`):** A pixel lantern: 1px `ink` outline, iron-grey frame, 2–3 warm glow tones. Respite has no HUD slot (§2), so this glyph serves Jade/WTHIT and recipe-viewer contexts only — the same role as Meridian's open book.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary | Midnight | `#141a3d` | Backgrounds, dark surfaces |
| Secondary | Deep Twilight | `#232e66` | Mid-tones, card backgrounds |
| Accent 1 | Moonlight Indigo | `#7C8EE8` | Headings, links, interactive elements |
| Accent 2 | Candleglow | `#F2C14E` | Warm emphasis, lantern glow, calls to action |
| Bright | Moonlight Bright | `#A6B4FF` | Hover states, heading-gradient end |
| Glow | Candleglow Pale | `#FFE29A` | Halos, glow falloff, particle warmth |

Shared neutrals (text and surfaces) follow the standard tokens as-is —
`--color-bone`, `--color-ash`, `--color-smoke`, `--color-ink`,
`--color-card`, `--color-elevated` — see concord
[`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §1–2.

### Typography

- **Heading gradient endpoints:** `#7C8EE8` → `#A6B4FF` (Moonlight Indigo → Moonlight Bright); Candleglow stays out of headings and marks warm emphasis only.
- Everything else per the standard (concord `design/DESIGN-SYSTEM.md` §3); in-game is the vanilla font, always.

---

## 2. HUD Decision

**No slot, by design.** The standard's test is *persistent ambient state the player needs while walking around*, and Respite carries none: the time-lapse is diegetic (the accelerating sky **is** the indicator), Weariness is an ordinary status effect with a vanilla effect icon, and the Chronometer's reading lives on the block — right-click feedback and a Jade/WTHIT line. Adding a badge would put a clock on the screen for a mod whose promise is that you read time from the world. Respite therefore ships no HUD element and no HUD accessors; its info surfaces are the status-effect icon, action-bar notifications, `/respite status`, and Jade/WTHIT.

---

## 3. Assets

The asset inventory — every source under `art/` and the final file it ships as — lives in [`ASSETS.md`](ASSETS.md). This document owns the look: the lantern branding family above, and the in-game families below.

- **Chronometer block** — a copper-cased face on smooth stone; the dial shows a sun/moon disc that sweeps through 8 visual phases across the day. Copper and redstone read as its materials; the dial disc uses warm Candleglow tones by day and Moonlight tones by night. Custom pixel art via the glyph pipeline.
- **Brew items** — Unsteeped Brew (a murky bottle with floating leaf flecks) and Caffeinated Brew (a warm cocoa-brown bottle with a faint steam wisp). Glass and cork read vanilla; the liquid carries the warmth of the palette without using sibling accents.
- **Weary effect icon** — a half-lidded eye in Bone on transparent, matching vanilla effect-icon weight and framing.
- **Sound** — vanilla for everything organic (drinking, block placement). One custom synthesized pair via the `.sfx` pipeline: the time-lapse onset and settle cues (soft, breathy risers — triggers and subtitles in `SPEC.md`).

---

## 4. Generation Prompts

No master is prompt-sourced: the logo and icon are composed deterministically by `art/glyphs/logo.gen.py` and `art/glyphs/icon.gen.py`, and the lantern glyph is `.glyph`-authored (`art/hud-icon-16.glyph`) — re-render any of them by re-running its generator or the pipeline, per DESIGN-SYSTEM §8. A Gemini prompt for an illustrated hero logo is retained as an exploration/upgrade path in `art/exploration/logo-gemini-prompt.md`.

---

## 5. Image References

`art/exploration/` holds the pixel-art lantern reference renders the icon and logo lanterns were traced from — `lantern-ref-iron.png` (traced) and `lantern-ref-wood.png` (rejected variant) — beside the retained Gemini logo prompt. The brand's in-game touchstones are vanilla's lantern sprite (the motif's native form), the dusk sky gradient (the palette's source), and the phantom's pale teal-grey (the antagonist the palette deliberately does not use).

---

## 6. Website & Listing Brand Notes

Content lives elsewhere — page copy in `site/` (rendered by the shared Concord template), store copy in `site/listing-*.md`; this section carries only the brand.

- **Accent usage:** Moonlight Indigo carries structure — headings, links, card borders, nav. Candleglow is rationed to warm emphasis: the hero lantern glow, primary buttons, and feature-card highlights. Body text and surfaces stay on the shared neutrals over the Midnight surface pair, declared once in `site.json`'s theme block.
- **Hero:** the full logo over the dark field, the lantern's halo as the page's one warm light source.
- **Gallery direction (1920×1080, vanilla or a light shader):** stars streaking over a base during a time-lapse night; a Chronometer wall powering lanterns at dusk; a phantom flock over a snow-capped peak on a new moon; a campfire with brews steeping; a player asleep beside a roaring furnace bank.
- **OG image:** full logo on Ink at 1200×630, per the standard.

---

## 7. Concord Context

Respite owns **vitality** — sleep, rest, weariness, phantoms, and how the night passes; it never touches difficulty (Tribulation), trades (Mercantile), loot (Prosperity), or enchanting (Meridian).

Its signature pair is checked against the full DESIGN-SYSTEM §2 table, reserved rows included. Candleglow sits in the suite's warm-gold family, which the pairing rule explicitly allows when the pair is distinct: gold-with-violet reads Meridian, gold-with-cyan reads Prosperity, amber-with-leaf reads Husbandry — **candle-with-indigo reads Respite**. Moonlight Indigo's nearest neighbors are Meridian's Arcane Purple `#7B2FBE` (vivid red-leaning purple vs. Respite's pale periwinkle blue) and Tempest's reserved Storm Blue `#4A7FB5` (steel daylight blue vs. Respite's violet-leaning night indigo) — and neither pair matches. Tribulation, Mercantile, Apothecary, and Stratum are clear on both accents.

Suite standards: concord [`VISION.md`](../../concord/VISION.md), [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md), [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md), [`API-STANDARD.md`](../../concord/API-STANDARD.md).

---

## Open Decisions

- **Weary icon:** half-lidded eye (current direction) vs. a guttering candle stub; decided when the glyph is authored against the vanilla effect-icon grid.
