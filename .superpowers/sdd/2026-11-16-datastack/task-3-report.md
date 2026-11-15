# Task 3 report: Mob merging

Status: DONE. Commit: see `git log` ("feat: merge nearby same-kind mobs").

## Files
- `src/main/java/th/park/datastack/mob/MobSignature.java` — `eligible(LivingEntity)`, `of(LivingEntity)`.
- `src/main/java/th/park/datastack/mob/MobMerger.java` — listener + sweep; `static tryMerge(LivingEntity)` (returns the entity now holding the stack, or null); `MobMerger.get()` instance accessor.
- `DataStack.java` — registers MobMerger; clears it on disable.

## Behaviour
- Eligible: `Mob` only, valid/alive; not EnderDragon/Wither/Warden; not tamed; custom name without `datastack:label` marker -> ineligible; not mid-death (`datastack:dying`); not leashed/riding/ridden; count < maxMobStack.
- Signature: type + adult/baby + sheep colour, villager profession/type/level, zombie villager profession, cat/frog (registry key), axolotl/parrot variant, horse colour+style, slime size.
- tryMerge: nearest eligible same-signature mob within mobRadius via `getNearbyEntitiesByType(Mob.class, ...)`; smaller count absorbed into larger, capped at maxMobStack (overflow stays on the smaller one); absorbed entity removed via `StackCount.set(…,0)`. Up to 8 merge rounds per call. Survivor keeps its own health (documented in javadoc).
- Triggers: CreatureSpawnEvent (+1 tick), EntitiesLoadEvent, sweep timer: 2 loaded chunks per tick round-robin (queue refilled from loaded chunks when empty).

## Test server verification (~/minecraft-test)
- 20x `summon cow 10 -60 10` + 5x `summon cow 11 -60 11 {Age:-24000}`; 5 s later `datastack info @e[type=cow]`: `COW 5dd5c20b… count=20`, babies `COW 1600130f… count=5` (Age -23619) separate. Named "Bob" cow and a lone cow 1 block from it stayed count=1. (Two other count=1 cows were pre-existing world cows far away.)
- 3 red (`{Color:14}`) + 3 white sheep 1 block apart: two stacks `count=3` each; `data get … Color` on one -> 14b.
- Wolf `{Owner:[I;1,2,3,4]}` next to 3 wild wolves: wild ones `count=3`, tamed `count=1` (Owner confirmed [I;1,2,3,4]).
- `systemctl --user restart mc-test` -> `COW 5dd5c20b… count=20`, babies count=5, wolves 3/1 unchanged.
- spark: `spark profiler start --timeout 60 --interval 1`, then 200 cows summoned on a 2-block grid, merged during the run. Profile https://spark.lucko.me/WiYWDPyiDT. Decoded the bytebin protobuf locally: Server thread 59862 ms sampled, DataStack frames 52 ms total (spawn lambda 37, sweep 9, onSpawn 5, StackCount.set 1) => ~0.043 ms/tick (< 1 ms). `spark tps` during run: tick median 4.9 ms, 95%ile 5.5 ms.
  (First attempt with `--thread Server` captured no thread data; profile 0Z9BAVsImK is unusable.)

## Concerns / notes
- 200 cows on a 40x20 grid end up as ~11 stacks, not 2: chain merging only joins mobs within 5 blocks of each other; stacks further apart stay separate until they wander close (by design of the radius).
- Observed Task 2 behaviour: `/kill @e[type=cow]` on an x20 stack kills one and leaves x19 (KILL cause treated as one-at-a-time). Maybe intended; flagging.
- Wolf stack x3 killed sheep on the test server — normal vanilla hunting, just noting the stacked wolf is a single attacker.
- Merging does not check line of sight/walls (brief does not ask).
- The 1-tick spawn lambda dominates cost when many mobs spawn at once; still well under budget.

## Fix round 1
Changes:
- eligible(): any non-empty equipment slot (that the mob can use) -> ineligible; custom name is ours only if LABEL marker present AND `Labels.isOurs(plainName)`.
- of(): adds despawnable/persistent (getRemoveWhenFarAway), sheep sheared, mooshroom variant, rabbit type, fox type, llama/trader llama colour, wolf variant (registry key), panda main/hidden gene, goat left/right horn, tropical fish pattern/body/pattern colour.
- onLoad merges deferred 1 tick.
Build: `./build.sh ~/minecraft` -> 6 tests successful, built.
Test server (difficulty easy + midnight for zombies, restored to peaceful after):
- 3x `summon zombie 40 -60 40 {PersistenceRequired:1b}` + `summon zombie 40 -60 41 {PersistenceRequired:1b,equipment:{mainhand:{id:"minecraft:diamond_sword",count:1}}}` -> `ZOMBIE 5500… count=3`, `ZOMBIE e5c3… count=1` (armed one not absorbed). (First attempt used legacy HandItems, which 26.3 ignores, so that zombie merged — not a bug.)
- 2 sheared + 2 unsheared sheep 1 block apart -> two stacks count=2 each.
- 2 brown + 2 red mooshrooms -> two stacks count=2 each.
- 20x `summon cow 20 -60 20` -> `COW 43f6… count=20`.
- Pig stack x7 renamed `data merge entity 0e44… {CustomName:"Bob"}` (keeps LABEL marker), then 2 pigs summoned 1 block away -> Bob stays count=7, new pigs form their own count=2; CustomName still "Bob".
