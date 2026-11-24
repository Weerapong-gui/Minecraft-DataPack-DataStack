package th.park.datastack.mob;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ZombieVillager;
import th.park.datastack.StackCount;

/**
 * Kinds that never stack: villagers (and wandering traders, through AbstractVillager) and zombie villagers.
 * Stacks of them made by older versions are split back into single mobs when their chunk loads.
 */
public final class NeverStack {
    private NeverStack() {}

    public static boolean is(Entity e) {
        return e instanceof AbstractVillager || e instanceof ZombieVillager;
    }

    /** Turns an old xN stack of a never-stack kind back into N separate mobs; returns how many were added. */
    public static long split(Entity e) {
        if (!is(e) || !(e instanceof LivingEntity le) || !le.isValid()) return 0;
        long count = StackCount.get(le);
        if (count <= 1) return 0;
        long made = 0;
        for (long i = 1; i < count; i++) {
            LivingEntity one = MobInteractListener.splitCopy(le, 1);
            if (one == null) break;
            // spread them a little so a big stack does not die of entity cramming in one spot
            double a = i * 2.399963; // golden angle
            double r = 0.35 * Math.sqrt(i);
            one.teleport(le.getLocation().add(Math.cos(a) * Math.min(r, 2.5), 0, Math.sin(a) * Math.min(r, 2.5)));
            made++;
        }
        StackCount.set(le, count - made);
        th.park.datastack.DataStack.get().getLogger().info("split " + le.getType() + " x" + count + " at "
            + le.getLocation().toVector().toBlockVector() + " into single mobs (" + made + " added)");
        return made;
    }
}
