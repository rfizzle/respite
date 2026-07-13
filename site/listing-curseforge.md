# Respite — Vitality Overhaul

**_Make the night count._**

![Respite logo](https://raw.githubusercontent.com/rfizzle/respite/master/art/logo.png)

**Also on [Modrinth](https://modrinth.com/mod/respite-vitality-overhaul)
and [GitHub Releases](https://github.com/rfizzle/respite/releases).**
Visit the [website](https://respite.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Respite is a vitality overhaul for **Minecraft 1.21.1 (Fabric)** — sleep, rest,
and the passage of night. Vanilla's bed is a skip button: you click, the screen
fades, and morning is simply there. Respite makes the night happen: sleeping
accelerates time visibly while the world keeps running, a hearty supper heals
you overnight, and phantoms trade their insomnia grudge for a real territory.

**In development.** The design and full behavioral spec are committed and
features are being built against them; this page describes the first release.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- Every feature independently tunable through `config/respite.json` —
  hot-reload with `/respite reload`.
- MIT licensed.

## Features

### Continuous Time-Lapse

Sleeping accelerates time — up to **60×** — instead of jumping to morning. The
world genuinely lives through the night: furnaces smelt (close to a full stack
over a night), crops grow, brewing stands bubble, and the stars streak
overhead. On a server every sleeper adds an equal share: 2 of 4 players in bed
run the night at 30×. Nobody's sleep is wasted, nobody's insomnia vetoes the
night, and a performance governor keeps struggling servers degrading gracefully
instead of stalling.

### Restful Saturation

Go to bed on a full hunger bar and your saturation converts into healing
overnight — every 2 points of saturation restore 1 heart by morning. A hearty
supper before bed becomes mechanically worth cooking; you wake healed, but
hungrier than you lay down.

### Phantoms of the Heights

Insomnia is gone. Phantoms spawn by territory instead: the high sky above
**Y=100** on any night, and everywhere under a **new moon** — one night in
eight. Mountains and sky bases get a native guardian, membranes are earned by
climbing, and `doInsomnia` stays the master phantom kill-switch.

### Weariness

Three in-game days without sleep slows your natural healing by 25% until you
rest — an ordinary status-effect icon, a gentle cost, and never a spawned
monster.

### The Chronometer

A copper-and-redstone timepiece block that emits a redstone signal tracking the
time of day — strength 1 at dawn, 8 at dusk, 15 before sunrise, one level per
80 seconds. Doors that bolt at dusk, lamps at nightfall, farms on a schedule.

### The Caffeinated Brew

Steep cocoa beans and leaves in a water bottle, cook it over a campfire, and
drink to clear Weariness and gain 90 seconds of Haste — all-nighters, done
deliberately. One drink, not a potion system; the brewing stand is untouched.

## Commands

Player command: `/respite status` — time-lapse rate, your time awake, Weary
state, and nights until the new moon. Operator commands cover config reload and
rest-state testing levers. Full reference:
[respite.rfizzle.com/commands.html](https://respite.rfizzle.com/commands.html)

## Optional integrations

Respite detects and integrates with these mods when present. **None are
bundled** — install whichever you already use.

- [Mod Menu](https://www.curseforge.com/minecraft/mc-mods/modmenu) — config screen entry
- [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config) — settings GUI
- [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) / [WTHIT](https://www.curseforge.com/minecraft/mc-mods/wthit)
  — Chronometer clock time and signal at a glance

**Enhanced by** its Concord siblings, never required: with
[Tribulation](https://www.curseforge.com/minecraft/mc-mods/tribulation-difficulty-overhaul) its
difficulty scaling reaches Respite's phantoms automatically; with
[Mercantile](https://www.curseforge.com/minecraft/mc-mods/mercantile-villager-overhaul) village
standing can put cocoa and brews on a trader's counter; with
[Prosperity](https://www.curseforge.com/minecraft/mc-mods/prosperity-loot-overhaul) a Chronometer
may turn up in a far-flung chest.

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API
- Java **21+**
