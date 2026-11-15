# DataStack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Paper 26.3 plugin that merges nearby same-kind mobs and dropped items into one entity with an `xN` label, without duping and without making the server heavier.

**Architecture:** Event-driven listeners on the Paper API; the true count lives in each entity's PersistentDataContainer. Pure decision logic (pickup split, death policy, label text, spawn budget) lives in Bukkit-free classes with JUnit tests; Bukkit glue is verified on the test server with a scripted checklist.

**Tech Stack:** Java (`--release 25`), Paper API 26.3 build 252, JUnit 5 (console standalone jar), plain `javac`/`jar` via `build.sh`.

**Spec:** `docs/superpowers/specs/2026-11-16-datastack-design.md`

## Global Constraints

- Paper 26.3 (build 252), Java 27 runtime, compile `--release 25`, no Gradle.
- Compile classpath = every jar under `~/minecraft/libraries` + `~/minecraft/versions/26.3/paper-26.3.jar` (same as `Minecraft-plugin-joinapproval/build.sh`).
- Mob merge radius 5 blocks; item merge radius 2.5 blocks (config defaults).
- Never merge: tamed animals, custom-named mobs, Ender Dragon, Wither, Warden.
- Count stored on the entity (PDC key `datastack:count`), survives restart.
- Label text exactly `x<N>`; no label when count is 1 (mobs) or ≤ vanilla max (items).
- Reducing load is the main goal: no per-tick full-world scans; any periodic sweep is chunk-batched and spread over ticks.
- Test on `~/minecraft-test` (unit `mc-test.service`) before `~/minecraft` (`minecraft.service`).

## Review Focus

1. Chunk unload/reload and server restart with a stack of 50 — count must come back as 50, label intact.
2. Player killing a stack with a looting/fire-aspect sword quickly (several hits per second) — exactly one death per lethal hit, no double-decrement.
3. Stack carrying gear (zombie with sword) killed 50 times — the sword drops at most once.
4. Item stack of 500 with a full inventory and a hopper underneath at the same time — total items conserved (never more, never less than 500).
5. Mob farm under natural spawning — total mobs counted via stacks never exceeds the vanilla category cap near a player.

Tests pinning these are in Tasks 2, 3, 6 and the Task 7 checklist.

---

## File Structure

```
build.sh                         build + run unit tests
lib/ (gitignored)                junit-platform-console-standalone jar
src/main/resources/plugin.yml
src/main/resources/config.yml
src/main/java/th/park/datastack/
  DataStack.java                 plugin entry: config, keys, listener registration
  Keys.java                      NamespacedKeys + get/set count helpers
  Labels.java                    pure: label text for a count
  StackCount.java                Bukkit glue: read/write count + refresh label on an entity
  mob/DeathPolicy.java           pure: DamageCause name -> ONE | WHOLE_STACK
  mob/MobDeathListener.java      one-at-a-time death, whole-stack env death, shared health, gear once
  mob/MobSignature.java          "same kind and state" key for an entity; eligibility rules
  mob/MobMerger.java             merge on spawn + chunk-batched sweep
  mob/MobInteractListener.java   name-tag split, shear/milk one yield, breed, transform
  mob/SpawnBudget.java           pure: may a natural spawn happen given summed counts
  mob/SpawnCapListener.java      cancel natural spawns over cap; despawn one-at-a-time
  item/PickupMath.java           pure: how many to take given count/maxStack/free space
  item/ItemStackListener.java    merge >64, player + hopper pickup, despawn by oldest
src/test/java/th/park/datastack/ LabelsTest, DeathPolicyTest, SpawnBudgetTest, PickupMathTest
```

---

### Task 1: Plugin skeleton, build, labels

**Files:**
- Delete: `pack.mcmeta`, `data/` (datapack scaffold)
- Create: `build.sh`, `src/main/resources/plugin.yml`, `src/main/resources/config.yml`, `DataStack.java`, `Keys.java`, `Labels.java`, `StackCount.java`, `src/test/.../LabelsTest.java`
- Modify: `README.md`, `.gitignore` (add `build/`, `lib/`)

**Interfaces:**
- Produces: `Labels.text(long count, int threshold) -> String|null` (null = no label); `StackCount.get(Entity) -> long` (default 1); `StackCount.set(Entity, long)` (writes PDC, refreshes label via `Labels`, `count<=0` removes entity); `DataStack.cfg()` exposing `mobRadius=5.0`, `itemRadius=2.5`, `maxMobStack=100`, `maxItemStack=10000`.

- [ ] **Step 1: Write failing test** `LabelsTest`: `text(1,1)==null`, `text(2,1).equals("x2")`, `text(64,64)==null`, `text(65,64).equals("x65")`, `text(1000,1).equals("x1000")`.
- [ ] **Step 2:** `build.sh` downloads `junit-platform-console-standalone-1.11.4.jar` to `lib/` if missing (verify sha256 from Maven Central), compiles main + test, runs tests, then jars `build/DataStack.jar` with `plugin.yml`/`config.yml`. Run `./build.sh ~/minecraft` on the server → FAIL (Labels missing).
- [ ] **Step 3:** Implement `Labels`, `Keys`, `StackCount`, `DataStack` (`api-version: '26.3'`). Label = entity custom name `x<N>` with `setCustomNameVisible(true)`; mark plugin-owned labels with PDC `datastack:label=1` so player-given names are distinguishable.
- [ ] **Step 4:** `./build.sh ~/minecraft` → tests PASS, jar built; copy to `~/minecraft-test/plugins/`, `systemctl --user restart mc-test`, log shows `Enabling DataStack`.
- [ ] **Step 5: Commit** `feat: replace datapack scaffold with DataStack plugin skeleton`.

### Task 2: Mob death rules

**Files:** Create `mob/DeathPolicy.java`, `mob/MobDeathListener.java`, test `DeathPolicyTest`.

**Interfaces:**
- Consumes: `StackCount.get/set`.
- Produces: `DeathPolicy.of(String damageCauseName) -> Outcome {ONE, WHOLE_STACK}`.

- [ ] **Step 1: Failing test** `DeathPolicyTest`: WHOLE_STACK for `LAVA, FIRE, FIRE_TICK, DROWNING, FALL, SUFFOCATION, VOID, HOT_FLOOR, FREEZE, STARVATION, CONTACT, LIGHTNING, MELTING`; ONE for `ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, ENTITY_EXPLOSION, BLOCK_EXPLOSION, CRAMMING, THORNS, MAGIC, POISON, WITHER, SONIC_BOOM`; unknown name → ONE.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement `DeathPolicy`. `MobDeathListener` on `EntityDeathEvent` for stacked mobs (count>1):
  - ONE: let vanilla drop for this one; spawn replacement via `entity.copy()`-equivalent (`world.spawn` of same type then copy signature-relevant state), set `count-1`, set replacement health = **dead mob's health before the lethal hit clamped to ≥ 1/2 heart** (shared health, spec 7a; use the pre-damage health recorded in `EntityDamageEvent` MONITOR), strip all equipment from the replacement (spec 11).
  - WHOLE_STACK: multiply drops: add `(count-1)` extra copies of each drop excluding equipment, set `droppedExp *= count`; no replacement.
  - Guard against double handling with a PDC `datastack:dying` flag.
- [ ] **Step 4:** Tests PASS; on test server: `/summon zombie` ×1, `/datastack set 50` (debug command added here: `/datastack set <n>` on the mob you look at, `/datastack info`) → hit-kill 3 times → label `x47`; give it a sword first → sword drops once; drop into lava pit → 47 zombies' rotten flesh/XP appears (minus burned).
- [ ] **Step 5: Commit** `feat: one-at-a-time and whole-stack mob deaths`.

### Task 3: Mob merging

**Files:** Create `mob/MobSignature.java`, `mob/MobMerger.java`.

**Interfaces:**
- Produces: `MobSignature.eligible(LivingEntity) -> boolean`; `MobSignature.of(LivingEntity) -> String` (type + baby/adult + per-type variant: sheep colour, villager profession/type/level, cat/frog/axolotl/parrot/horse variants, slime size, zombie villager profession; Bukkit-specific, no unit test); `MobMerger.tryMerge(LivingEntity)`.

- [ ] **Step 1:** Eligibility: `Mob` only; not `Tameable#isTamed`; no player-given custom name (custom name present and `datastack:label` absent → ineligible); not EnderDragon/Wither/Warden; not leashed, not riding/ridden; count < `maxMobStack`.
- [ ] **Step 2:** `tryMerge`: find nearest eligible same-signature mob within `mobRadius` (`getNearbyEntitiesByType`), absorb the one with the smaller count into the larger (sum ≤ max; overflow stays), remove absorbed. Triggers: `CreatureSpawnEvent` (1 tick later), `EntitiesLoadEvent`, and a sweep task that processes at most 2 loaded chunks per tick round-robin.
- [ ] **Step 3:** On test server: `/summon cow` ×20 in a pen → within ~5 s one cow `x20`; 5 baby cows stay separate from adults; red sheep don't merge with white; a tamed wolf next to wolves never merges; restart `mc-test` → `x20` still there (Review Focus 1).
- [ ] **Step 4:** spark: `/spark profiler start` 60 s with 200 summoned cows before/after merge; DataStack's share of tick < 1 ms/tick.
- [ ] **Step 5: Commit** `feat: merge nearby same-kind mobs`.

### Task 4: Interactions (name tag, shear/milk, breed, transform)

**Files:** Create `mob/MobInteractListener.java`.

- [ ] **Step 1:** `PlayerInteractEntityEvent` with NAME_TAG on stack (count>1): cancel, spawn single copy with the tag's name (no `datastack:label`), consume the tag (survival), stack `count-1`.
- [ ] **Step 2:** `PlayerShearEntityEvent` / milking (`PlayerInteractEntityEvent` bucket/bowl on cow/mooshroom/goat): leave vanilla yield (one mob); for sheep shear only the front mob's wool state, no multiplication.
- [ ] **Step 3:** `EntityBreedEvent`: vanilla breeding of one pair; baby spawns as normal and goes through `tryMerge`.
- [ ] **Step 4:** Transforms: `EntityTransformEvent` (cure, lightning, drowning, etc.) and baby→adult (detect when a stacked baby's age reaches 0; if Paper 26.3 has no age event, check stacked babies in the chunk sweep). Exactly one member transforms: cancel/undo on the stack, spawn one entity of the new kind, `tryMerge` it, original stack `count-1`, stays baby/old kind with its age reset.
- [ ] **Step 5:** Test server: name tag on `x10` villagers → one named villager + `x9`; shear `x10` sheep → 1–3 wool total; lightning (`/summon lightning_bolt`) on `x5` pigs → one zombified piglin + `x4` pigs. **Commit** `feat: name tag split and one-at-a-time interactions`.

### Task 5: Mob cap accounting and despawn one-at-a-time

**Files:** Create `mob/SpawnBudget.java`, `mob/SpawnCapListener.java`, test `SpawnBudgetTest`.

**Interfaces:** `SpawnBudget.allows(long summedCountNearby, int categoryLimit, int playersInRange) -> boolean` = `summed < categoryLimit * max(1, playersInRange)` (simplified per-player cap; agent's call).

- [ ] **Step 1: Failing test:** `allows(49,50,1)` true; `allows(50,50,1)` false; `allows(90,50,2)` true; `allows(100,50,2)` false.
- [ ] **Step 2:** Implement; `PreCreatureSpawnEvent` (reason NATURAL/CHUNK_GEN off): sum `StackCount.get` of same-category mobs within 128 blocks of the nearest player; cancel when not allowed. Category limits from `world.getSpawnLimit(SpawnCategory)`.
- [ ] **Step 3:** Despawn: `EntityRemoveEvent` with cause `DESPAWN` on count>1 → respawn copy at same spot with `count-1` and same health (no gear). Must not trigger for chunk unload (`UNLOAD` cause).
- [ ] **Step 4:** Test server (temporarily switch to survival-mode test: `/gamerule doMobSpawning true`, `/difficulty normal`, night, dark platform 40 blocks away): after 5 min `/datastack info` shows summed hostile count ≤ 50 (Review Focus 5); walk 130 blocks away → stack counts drop one at a time.
- [ ] **Step 5: Commit** `feat: count stacks against mob cap; despawn one at a time`.

### Task 6: Item stacking

**Files:** Create `item/PickupMath.java`, `item/ItemStackListener.java`, test `PickupMathTest`.

**Interfaces:** `PickupMath.take(long count, int maxStack, int freeSlots, int partialSpace) -> long` = `min(count, freeSlots*maxStack + partialSpace)`.

- [ ] **Step 1: Failing test:** `take(500,64,3,0)==192`; `take(100,64,3,0)==100`; `take(500,64,0,10)==10`; `take(5,1,3,0)==3`; `take(500,64,0,0)==0`.
- [ ] **Step 2:** Implement. Listener:
  - `ItemMergeEvent`: if `isSimilar`, cancel vanilla, add counts in PDC (cap `maxItemStack`), keep the target, set target's `ticksLived`/age to the **older** of the two (spec 17), remove source. ItemStack amount on the entity = `min(count, maxStack)`; label via `Labels.text(count, maxStack)`.
  - `PlayerAttemptPickupItemEvent` / `EntityPickupItemEvent`: compute `take`, add that many to inventory manually, cancel vanilla, `count -= taken`.
  - `InventoryPickupItemEvent` (hopper): move one hopper-transfer worth, decrement count, cancel vanilla when count remains.
  - Lava/explosion: vanilla destroys the entity → whole stack gone (spec 18), nothing to do.
- [ ] **Step 3:** Test server: `/give` + drop 500 stone in one spot → one item `x500`; pick up with 3 free slots → inventory 192, ground `x308`; hopper under `x308` → drains to 0 at normal speed; both at once → `count_ground + inventory + hopper == 500` (Review Focus 4); enchanted vs plain swords don't merge.
- [ ] **Step 4: Commit** `feat: stack dropped items beyond 64`.

### Task 7: Live verification and release

- [ ] **Step 1:** Full checklist on test server: Tasks 2–6 checks + Review Focus 1–5, plus a Bedrock client (`.verpong321`) sees `xN` labels.
- [ ] **Step 2:** spark 10-min profile on test server with a 200-mob farm, plugin on vs off: MSPT with plugin ≤ MSPT without.
- [ ] **Step 3:** Update README (features, config, `/datastack` command, build). Commit `docs: DataStack usage`; push.
- [ ] **Step 4:** Only after owner OK: copy jar to `~/minecraft/plugins/`, `systemctl --user restart minecraft`, confirm `Enabling DataStack` and no errors.
