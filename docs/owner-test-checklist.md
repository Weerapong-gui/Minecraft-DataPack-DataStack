# DataStack owner test checklist (in-game, test server first)

These need a real player (or a Bedrock client) and cannot be driven from the console.
Use survival on the test server (`/test`). Restore afterwards: `doMobSpawning`, `/difficulty peaceful`, time.

## Display
- [ ] Bedrock client (`.verpong321`) sees the `xN` label above a stack: label visible, count correct.

## Mob death and loot (Task 2)
- [ ] Kill an x10 mob by hand: one dies, x9 remains with the same health.
- [ ] Kill an x10 mob in a lava farm / with fall damage: whole stack dies, drops and XP are multiplied by N (lava with no recent player hit gives 0 XP, as vanilla).
- [ ] Leashed mob stack: kill one, exactly one lead drops, the replacement is not leashed.

## Interaction (Task 4)
- [ ] Name tag on an x10 villager: one villager gets the name, x9 stays; tag consumed in survival, not in creative; the named one never re-merges.
- [ ] Shear an x10 sheep by hand: 1-3 wool, one sheared sheep leaves, x9 stays woolly.
- [ ] Milk a cow/goat stack (bucket) and a mooshroom (bowl): one item, stack count unchanged.
- [ ] Breed a stack with a single (or two stacks): one baby, parents keep counts, cannot be re-fed at once; baby merges with nearby babies.
- [ ] Zombie villager cure (weakness + golden apple) on a stack: only one cures, rest stay.
- [ ] Zombie stack drowning; husk to zombie; skeleton stack in powder snow; tadpole to frog: one transforms per event, rest stay count-1.
- [ ] Piglin/hoglin stack in the overworld: about one converts per ~15 s, not all at once.
- [ ] Shear an x10 mooshroom stack: one cow + mooshroom x9 + 5 mushrooms.

## Spawn cap and despawn (Task 5)
- [ ] `/gamerule doMobSpawning true`, `/difficulty normal`, `/time set night`, stand 40 blocks from a dark platform 5 min: sum of hostile `xN` within 128 blocks stays <= the monster limit (check `spawn-limits` in bukkit.yml, default 70).
- [ ] Big hostile stacks nearby (`/datastack set 50` on a zombie): new hostiles stop spawning.
- [ ] Persistent stack (name-tagged, or `PersistenceRequired:1b` zombie with `/datastack set 100`) does not block natural spawns.
- [ ] Stand 40-120 blocks from an x50 zombie for a few minutes: count drops by one now and then, same health, no gear.
- [ ] Walk >130 blocks away: the whole stack disappears at once (by design).
- [ ] Log out/in and restart the server: stacks keep their counts.

## Items (Task 6)
- [ ] Drop 500 stone in one spot: one `x500` item.
- [ ] Exactly 3 empty slots, walk over it: inventory +192, ground shows `x308`; pickup animation and sound play.
- [ ] 60 stone in one slot, no empty slots, walk over the stack: +4, ground -4.
- [ ] Walk over 5 stacked swords with 2 empty slots: you get 2, ground shows `x3`.
- [ ] Stand on the stack while a hopper below drains it: inventory + hopper + chest + ground = 500.
- [ ] Allay/fox near an `x500` stack does not take it; a normal <=64 stack behaves as vanilla.
- [ ] Throw the stack in lava: it disappears completely.
