package th.park.datastack.mob;

import java.util.ArrayDeque;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;
import th.park.datastack.DataStack;
import th.park.datastack.StackCount;

/**
 * Merges nearby same-kind mobs. Triggered by spawns (1 tick later), entity loads,
 * and a cheap sweep that visits at most {@link #CHUNKS_PER_TICK} loaded chunks per tick.
 */
public final class MobMerger implements Listener {
    private static final int CHUNKS_PER_TICK = 2;
    /** Bound on merges per tryMerge call, so one call stays cheap. */
    private static final int MAX_ROUNDS = 8;

    private static MobMerger instance;
    private final Plugin plugin;
    private final ArrayDeque<long[]> queue = new ArrayDeque<>(); // {worldIndex, chunkX, chunkZ}

    public MobMerger(Plugin plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepTick, 20L, 1L);
    }

    public static MobMerger get() { return instance; }

    public static void clear() { instance = null; }

    /**
     * Merges e with its nearest eligible same-kind neighbours within mobRadius.
     * The smaller stack is absorbed into the larger (capped at maxMobStack; overflow stays).
     * Health: the surviving entity keeps its own current health; absorbed health is not summed or averaged.
     * Returns the entity that now holds e's stack (may be e), or null if e was fully absorbed.
     */
    public static LivingEntity tryMerge(LivingEntity e) {
        LivingEntity cur = e;
        for (int round = 0; round < MAX_ROUNDS && cur != null && MobSignature.eligible(cur); round++) {
            LivingEntity other = nearest(cur);
            if (other == null) break;
            long a = StackCount.get(cur), b = StackCount.get(other);
            LivingEntity big = a >= b ? cur : other, small = big == cur ? other : cur;
            long bigN = Math.max(a, b), smallN = Math.min(a, b);
            long moved = Math.min(smallN, DataStack.get().cfg().maxMobStack() - bigN);
            if (moved <= 0) break;
            StackCount.set(big, bigN + moved);
            StackCount.set(small, smallN - moved); // 0 removes the absorbed entity
            if (small == cur && smallN - moved <= 0) cur = big; // e's stack now lives on in big
            if (smallN - moved > 0) break; // big is full now
        }
        return cur != null && cur.isValid() ? cur : null;
    }

    private static LivingEntity nearest(LivingEntity e) {
        double r = DataStack.get().cfg().mobRadius();
        String sig = MobSignature.of(e);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : e.getWorld().getNearbyEntitiesByType(Mob.class, e.getLocation(), r)) {
            if (m == e || !MobSignature.eligible(m) || !sig.equals(MobSignature.of(m))) continue;
            double d = m.getLocation().distanceSquared(e.getLocation());
            if (d < bestD) { bestD = d; best = m; }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent ev) {
        LivingEntity le = ev.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (le.isValid()) tryMerge(le); }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(EntitiesLoadEvent ev) {
        var list = ev.getEntities();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity en : list) if (en instanceof LivingEntity le && le.isValid()) tryMerge(le);
        }, 1L);
    }

    private void sweepTick() {
        if (queue.isEmpty()) refill();
        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            long[] c = queue.poll();
            World w = Bukkit.getWorlds().size() > c[0] ? Bukkit.getWorlds().get((int) c[0]) : null;
            if (w == null || !w.isChunkLoaded((int) c[1], (int) c[2])) continue;
            Chunk ch = w.getChunkAt((int) c[1], (int) c[2]);
            for (Entity en : ch.getEntities())
                if (en instanceof Mob m && m.isValid()) tryMerge(m);
        }
    }

    private void refill() {
        var worlds = Bukkit.getWorlds();
        for (int wi = 0; wi < worlds.size(); wi++)
            for (Chunk ch : worlds.get(wi).getLoadedChunks())
                queue.add(new long[] {wi, ch.getX(), ch.getZ()});
    }
}
