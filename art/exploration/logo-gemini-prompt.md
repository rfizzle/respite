# Logo — Gemini prompt (exploration / upgrade path)

The shipped `art/logo.png` is generated deterministically by
`art/glyphs/logo.gen.py`. This prompt is the retained alternative for a
Gemini-illustrated hero logo in the siblings' style, should the brand ever
trade the generated master for a painted one:

> Pixel art logo for a Minecraft mod named "RESPITE". A dark stone brickwork
> frame surrounds a deep midnight-indigo night sky (#141a3d to #232e66). At
> the center, a single iron-framed hanging lantern on a short chain glows warm
> golden-yellow (#F2C14E) with a soft pale halo (#FFE29A) that lights the
> nearest stone bricks. A few faint single-pixel stars are scattered sparsely
> — no constellations, no moon. Below the frame, "RESPITE" in a blocky pixel
> font with a soft indigo gradient (#7C8EE8 to #A6B4FF), and the subtitle
> "MINECRAFT VITALITY OVERHAUL" in small pixel type. Dark background
> (#0a0a0a). Crisp pixel art, limited palette, no anti-aliasing.

Reference renders used to trace the lantern structure (see
`icon.gen.py`/`logo.gen.py`): `lantern-ref-iron.png` (traced),
`lantern-ref-wood.png` (rejected variant — wood cap reads less "iron lantern"
against the indigo field).
