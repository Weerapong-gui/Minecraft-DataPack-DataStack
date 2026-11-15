# DataStack — design

Date: 2026-11-16
Status: draft for approval

## Intent and why

The owner runs a small Paper 26.3 survival server for friends on an old
machine (Intel i5-3470, 4 cores, ~4 GB heap for the game). Dropped items and
mobs piling up in farms and around players is a constant source of server
load. DataStack merges nearby items and mobs of the same kind into a single
entity that shows the total above it as `xN`, so the server ticks one entity
instead of many, and items can stack far beyond 64.

**Reducing server load is the main goal.** Anything that makes the server
heavier than without the plugin is a failure, even if the stacking works.

The project was started as a datapack; it was switched to a **Paper plugin**
because event-driven Java is much lighter than tick-scanning commands (owner
agreed). The repo is `Weerapong-gui/Minecraft-DataPack-DataStack`; the current
datapack scaffold in it gets replaced by the plugin. Renaming the repo is
optional and not part of this work.

## Environment / constraints

- Paper 26.3 (build 252), Java 27 runtime, compile with `--release 25`.
- Runs on the `survival` backend behind a Velocity 4.3.1 proxy. Bedrock
  players join through Geyser + Floodgate on the proxy; the plugin must work
  for them too (it only touches server-side entities, so no client work).
- Existing plugins on the backend: Floodgate, JoinApproval, Chunky, spark.
- Paper config already in place: item merge radius 2.5 (`spigot.yml`),
  `max-entity-collisions: 2`, monster spawn limit 50, simulation distance 6.
- Build style: same as the owner's other plugins (plain `javac` + `jar` via a
  `build.sh`, no Gradle), so the owner can rebuild on the server.
- Build reference: `Weerapong-gui/Minecraft-plugin-joinapproval` has the
  `build.sh` to copy. It compiles against the Paper install on the server:
  classpath = every jar under `~/minecraft/libraries` plus
  `~/minecraft/versions/26.3/paper-26.3.jar` (contains `paper-api-26.3.build.252-stable`).
- Must be testable on the on-demand test server (`/test`) before going live.
  Test server lives at `~/minecraft-test` on the same machine
  (`plugins/` folder there; systemd user unit `mc-test.service`). Live server
  is `~/minecraft` (`minecraft.service`). Deploy = copy jar into `plugins/`
  and restart the unit.
- Thai text is fine in player-facing messages (the server has a Thai font pack).

## Goals

### Mobs
1. Mobs of the same kind near each other merge into one entity showing
   `xN` above it. Merge radius about **5 blocks** (owner accepted this guess).
2. Mobs only merge when they are the same type **and** the same state —
   e.g. baby vs adult, sheep colour, villager profession/level do not mix.
3. **Never merged:** tamed animals; any mob with a custom name; bosses
   (Ender Dragon, Wither, Warden — owner accepted this guess).
4. **Killing:** a player (or anything that is not "environment", see 7) kills
   **one at a time**. Each death drops normal loot and XP for one mob, and
   the stack count goes down by one (x50 → x49). The rest stay where they are.
5. **Name tag splits one off:** using a name tag on a stack takes one mob out
   of the stack with that name; the rest stay stacked. Works on every mob
   type, villagers included. Named mobs never merge again.
6. **Transformations happen one at a time.** When one member changes (a baby
   grows up, a zombie villager is cured, a pig is struck by lightning, etc.),
   that one leaves the stack and joins a nearby stack of its new kind if one
   is in range, otherwise it becomes a new single mob / new stack.
7. **Environmental death kills the whole stack** (lava, fire, drowning, fall,
   suffocation, void, etc.). Loot and XP are dropped **for every member**
   (x50 → loot and XP of 50). Drops that land in lava burn as usual.
   Owner decided these are **not** environment and kill **one at a time**
   (rule 4): explosions (creeper, TNT), entity cramming, attacks by other
   mobs or iron golems, sweeping-edge hits, thorns.
7a. **Damage that does not kill is shared by the whole stack** (owner
   decision). The stack keeps one current health; when the front mob dies,
   the next one comes in with **that same health**, not full health. So a
   fall-damage XP farm that leaves mobs at half a heart works for every
   member of the stack.
7b. **Hostile mobs despawn one at a time** (owner decision). Vanilla
   despawn rules (distance from players) apply per member: a despawn removes
   one from the stack (x50 → x49) instead of the whole stack. Stacks must not
   live forever or grow past the mob cap.
8. **Breeding** works one at a time; the baby joins stacks normally
   (owner accepted this guess).
9. **Mob cap is not cheated.** The game's natural spawning must treat a stack
   of N as N mobs, so farms produce about what they would without the plugin.
   Owner asked for "tricking the system so the mob count equals the stacked
   count". Natural spawns that would exceed the cap counted this way are
   prevented.

### Anti-dupe (owner requirement: "equal to one mob")
10. Shearing a stacked sheep, milking a stacked cow, and similar interactions
    give the yield of **one** mob.
11. **Equipment/held items drop only once.** Gear belongs to the one entity
    that wears it; when it dies and the next member takes its place, the new
    one has no gear. Gear can never be multiplied by killing a stack.
    (Mechanism was the agent's call; owner asked only that dupes be prevented.)
12. The stack count is stored on the entity itself (persistent data), so it
    survives chunk unloads, server restarts and crashes. No mobs lost or gained.

### Items
13. Identical dropped items within about **2.5 blocks** (same as Paper's
    current setting) merge into one item entity showing `xN`, with no 64 cap.
14. Items must match exactly (type and all data: enchantments, names, etc.)
    to merge. Unstackable items (swords, armour, buckets) can also stack on
    the ground; picking up gives them one per slot.
15. **Picking up takes only what fits.** Example: x500 stone, 3 free slots →
    player gets 3 × 64 = 192, the stack stays on the ground as x308.
16. **Hoppers** pull from the stack at their normal rate; the stack shrinks
    the same way as when a player picks up.
17. **Despawn timer** follows the **oldest** item in the stack, so merging
    can never keep items alive forever.
18. Items that hit lava/fire or explosions are destroyed as a whole stack,
    like normal items.

### Display
19. The `xN` label floats above the entity like a name tag. A single mob or a
    normal item (count ≤ its vanilla max for items, 1 for mobs) shows no label.

## Non-goals
- Stacking of non-mob, non-item entities (XP orbs, arrows, boats, minecarts,
  armour stands, etc.).
- Stacking across different worlds/dimensions or across chunks that are not
  loaded.
- Any client-side mod, resource pack or Bedrock pack.
- Changing the test server; it is only used to try the plugin.
- Commands/GUI for players beyond what is needed to configure and debug.

## Anti-goals (would make this a failure even if it "works")
- The server is heavier with the plugin than without it (measure with spark).
- Any way to duplicate items, gear, wool, milk, XP or mobs.
- Farms producing noticeably more than vanilla because of the mob cap.
- Losing mobs or items on restart/crash.
- Tamed pets or named mobs ever being absorbed into a stack.

## Left to the builder
- Exact merge radii and how often merging is checked (event-driven where
  possible; any periodic sweep must be cheap and spread over ticks).
- Maximum size per stack, if any (agent's earlier guess was x100 for mobs;
  owner did not confirm a number, so pick a sensible, configurable default).
- How the label is rendered (custom name vs text display), colours, and how
  it looks for Bedrock players, as long as it reads `xN`.
- Exactly which properties count as "same state" for each mob type.
- How mob-cap accounting is implemented (e.g. cancelling natural spawns
  based on summed stack counts per player/chunk area).
- Config file layout, admin/debug command, and default values.
- Behaviour details not covered above should follow "act like N separate
  vanilla mobs/items as closely as possible, without being able to dupe".
