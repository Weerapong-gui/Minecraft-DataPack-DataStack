# DataStack (Paper plugin, Minecraft 26.3)

Merges nearby same-kind mobs and dropped items into one entity and shows the
total above it as `xN`, allowing far more than 64.

## Features
- Mobs of the same kind and state (age, colour/variant, no gear, not tamed or renamed) merge into one stack with an `xN` label. Labels are muted (gray name, dull gold count); a lone mob has no label. Villagers, wandering traders and zombie villagers never stack; old stacks of them split back into single mobs when their chunk loads.
- Killing a stack removes one mob at a time; whole-stack deaths (lava, void, fall) multiply drops and XP by N.
- Name tags, shearing, milking, breeding, conversions (zombie villager cure, drowning, etc.) act on one mob and leave the rest.
- Stacks count toward Paper's mob cap (N each), and random despawn shrinks a stack by one instead of removing it.
- Dropped items merge past 64 up to `max-item-stack`; pickup, hoppers and mob pickup are conserved (no dupes).
- The label is a normal custom name, so Bedrock (Floodgate) clients should see it too (see the checklist).

## Config (`plugins/DataStack/config.yml`)
| Key | Default | Meaning |
|---|---|---|
| `mob-radius` | `5.0` | Blocks within which same-kind mobs merge |
| `item-radius` | `2.5` | Blocks within which same-kind items merge |
| `pull-radius` | `4.0` | Blocks within which a bigger item pile flies into a smaller one |
| `max-mob-stack` | `10000` | Maximum mobs per stack |
| `max-item-stack` | `10000` | Maximum items per stack |
| `label-range` | `6.0` | Show `xN` labels only to players within this many blocks (labels draw through walls); `0` = any distance. Needs the PacketEvents plugin, otherwise ignored |

## Commands (op, permission `datastack.debug`)
- `/datastack set <n> [selector]` - set a stack's count (minimum 1).
- `/datastack info [selector]` - print type, UUID, count and health of stacks.

## Build
Needs a Paper server install for the classpath (no Gradle), with `packetevents*.jar` in its `plugins/`:

    ./build.sh /path/to/paper-server

`/path/to/paper-server` is the folder containing `libraries/` and `versions/`.
It runs the unit tests (JUnit console jar is fetched into `lib/` and checksum-verified),
then writes `build/DataStack.jar`. Drop it in `plugins/`.

## Deploy
Try it on a test server first, run the [owner checklist](docs/owner-test-checklist.md),
and only then copy the jar to the live server.

**Before going live:** set `settings.log-named-deaths: false` in `spigot.yml`. The `xN` label is a custom name, so otherwise every kill of a stacked mob logs a line to the console.
