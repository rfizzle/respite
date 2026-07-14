# Respite — Vitality Overhaul

**_Make the night count._**

![Respite logo](https://raw.githubusercontent.com/rfizzle/respite/master/art/logo.png)

**Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/respite-vitality-overhaul)
and [GitHub Releases](https://github.com/rfizzle/respite/releases).**
Visit the [website](https://respite.rfizzle.com) for the full feature
list, config reference, and command guide.

---

Respite is a vitality overhaul for **Minecraft 1.21.1 (Fabric)** — sleep, rest,
and the passage of night. Vanilla's bed is a skip button: you click, the screen
fades, and morning is simply there. Respite makes the night happen: sleeping
accelerates time visibly while the world keeps running, a hearty supper heals
you overnight, and phantoms trade their insomnia grudge for a real territory.

**An overhaul, not an add-on.** Every feature toggles on its own, and outside
the night the world you know is untouched — world generation, daytime mobs, and
every block and item work exactly as before.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- Every feature independently tunable through `config/respite.json` —
  hot-reload with `/respite reload`.
- MIT licensed.

## Features

### Continuous Time-Lapse

Sleeping accelerates time — up to **20×** by default — instead of jumping to
morning. The world genuinely lives through the night: furnaces smelt (close to a
full stack over a night), crops grow, brewing stands bubble, and the stars
streak overhead. On a server every sleeper adds an equal share: 2 of 4 players
in bed run the night at 10×. Nobody's sleep is wasted, nobody's insomnia vetoes the
night, and a performance governor keeps struggling servers degrading gracefully
instead of stalling. A quiet chat whisper names who climbs into or out of bed
with the running tally, and an AFK sleeper drops out of the share entirely — a
session left at the keyboard neither speeds the night nor drags it down.

### Restful Saturation

Go to bed on a full hunger bar and your saturation converts into healing
overnight — every 2 points of saturation restore 1 heart by morning. A hearty
supper before bed becomes mechanically worth cooking; you wake healed, but
hungrier than you lay down. Sleep runs deeper the darker the moon: the heal per
point ramps across the lunar cycle — plain at the full moon, up to double on the
new moon, the darkest night one in eight — when the same reserve heals like a
feast.

### Phantoms of the Heights

Insomnia is gone. Phantoms spawn by territory instead: the high sky above
**Y=100** on any night, and everywhere under a **new moon** — one night in
eight. Mountains and sky bases get a native guardian, membranes are earned by
climbing, and `doInsomnia` stays the master phantom kill-switch.

### Weariness

Three in-game days without sleep slows your natural healing by 25% until you
rest — an ordinary status-effect icon, a gentle cost, and never a spawned
monster. The ladder runs the other way too: waking from a full night in bed — a
real bed or a bedroll alike — leaves you **Well-Rested**, a beneficial effect
that heals you 50% faster for two minutes, then fades. It reads as one coherent
morning with Restful Saturation, never a double reward, and switches off on its
own toggle.

### The Chronometer

A copper-and-redstone timepiece block that emits a redstone signal tracking the
time of day — strength 1 at dawn, 8 at dusk, 15 before sunrise, one level per
80 seconds. Doors that bolt at dusk, lamps at nightfall, farms on a schedule.
A comparator off the same block reads a different clock — moon fullness, 0 on
the new moon to 15 on the full — so a build can arm for the dark nights when
phantoms own the sky. Sneak-right-click to set an alarm hour, and the block
rings a bell once when it arrives. Ring a clock with copper instead and the
**pocket chronometer** goes in your inventory: a carried, read-only timepiece
whose tooltip reads the hour, and after dark the moon's phase and the nights
until the new moon, alongside your own days awake.

### The Caffeinated Brew

Steep cocoa beans and leaves in a water bottle, cook it over a campfire, and
drink to clear Weariness and gain 90 seconds of Haste — all-nighters, done
deliberately. One drink, not a potion system; the brewing stand is untouched.

### The Bedroll

Craft a bedroll from a row of string over a row of any wool. Right-click the
ground to unroll it and turn in — placed and asleep in one action — then it
rolls back into your inventory when you wake. It obeys every vanilla bed rule
and never explodes in the Nether, but it never sets your spawn: morning comes to
you where you lie. It sleeps like a bed for that night — the time-lapse runs,
your days-awake count resets — while overnight Restful Saturation heals at half
a real bed's rate, so a bed at home stays the better night's sleep.

## Commands

Player command: `/respite status` — time-lapse rate, your time awake, Weary
state, and nights until the new moon. Operator commands cover config reload and
rest-state testing levers. Full reference:
[respite.rfizzle.com/commands.html](https://respite.rfizzle.com/commands.html)

## Optional integrations

Respite detects and integrates with these mods when present. **None are
bundled** — install whichever you already use.

- [Mod Menu](https://modrinth.com/mod/modmenu) — config screen entry
- [Cloth Config](https://modrinth.com/mod/cloth-config) — settings GUI
- [Jade](https://modrinth.com/mod/jade) / [WTHIT](https://modrinth.com/mod/wthit)
  — Chronometer clock time and signal at a glance

**Enhanced by** its Concord siblings, never required: with
[Tribulation](https://modrinth.com/mod/tribulation-difficulty-overhaul) its
difficulty scaling reaches Respite's phantoms automatically; with
[Mercantile](https://modrinth.com/mod/mercantile-villager-overhaul) village
standing can put cocoa and brews on a trader's counter; with
[Prosperity](https://modrinth.com/mod/prosperity-loot-overhaul) a Chronometer
may turn up in a far-flung chest.

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API
- Java **21+**
- Install on the **server** and every **client** — the time-lapse sky and the
  weariness dim are drawn client-side.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for 1.21.1.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods/`
   folder.
3. Download Respite from this Modrinth page (via the Modrinth App, Prism
   Launcher's Modrinth tab, or a manual jar drop) and place it into `mods/`
   as well.
4. *(Optional)* Add Mod Menu and Cloth Config for the in-game settings screen.

Config generates at `config/respite.json` on first launch.

## Links

- **Website:** <https://respite.rfizzle.com>
- **GitHub Releases (canonical downloads):** <https://github.com/rfizzle/respite/releases>
- **CurseForge:** <https://www.curseforge.com/minecraft/mc-mods/respite-vitality-overhaul>
- **GitHub:** <https://github.com/rfizzle/respite>
- **Report an issue:** <https://github.com/rfizzle/respite/issues>
- **Changelog:** <https://respite.rfizzle.com/changelog.html>

## Companion mods

Respite is part of [Concord](https://github.com/rfizzle/concord) — a
modular collection of system overhauls. Install any, combine all:

- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

## License & credits

Licensed under the [MIT License](https://github.com/rfizzle/respite/blob/master/LICENSE).
© 2026 rfizzle. Respite is not affiliated with Mojang Studios or Microsoft.
