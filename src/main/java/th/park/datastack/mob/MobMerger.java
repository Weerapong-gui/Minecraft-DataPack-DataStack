package th.park.datastack.mob;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
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
 * and a cheap sweep that visits at most {@link #MOBS_PER_TICK} mobs per tick (resuming mid-chunk).
 */
public final class MobMerger implements Listener {
    private static final int MOBS_PER_TICK = 64;
    /** Bound on merges per tryMerge call, so one call stays cheap. */
    private static final int MAX_ROUNDS = 8;

    private static MobMerger instance;
    private final Plugin plugin;
    private final ArrayDeque<long[]> queue = new ArrayDeque<>(); // {worldIndex, chunkX, chunkZ}
    /** Mobs of the chunk being swept and the next one to visit. */
    private Entity[] pending = new Entity[0];
    private int pendingIdx;
    /** Signatures computed during one sweep tick (null outside a sweep): each mob's at most once. */
    private static Map<LivingEntity, String> sigCache;
    /** Eligibility seen during one sweep tick; the chosen target is always re-checked live. */
    private static Map<LivingEntity, Boolean> eligCache;
    /** Line-of-sight results during one sweep tick, keyed by the (symmetric) entity-id pair. */
    private static Map<Long, Boolean> losCache;
    /** A full pass over the loaded chunks starts at most this often (spawn/load hooks merge at once). */
    private static final int MIN_PASS_TICKS = 40;
    private int ticksSinceRefill = MIN_PASS_TICKS;

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

    private static String sig(LivingEntity e) {
        if (sigCache == null) return MobSignature.of(e);
        return sigCache.computeIfAbsent(e, MobSignature::of);
    }

    private static boolean eligible(LivingEntity m) {
        if (eligCache == null) return MobSignature.eligible(m);
        return eligCache.computeIfAbsent(m, MobSignature::eligible);
    }

    private static boolean sees(LivingEntity a, LivingEntity b) {
        if (losCache == null) return a.hasLineOfSight(b);
        int x = a.getEntityId(), y = b.getEntityId();
        long key = ((long) Math.min(x, y) << 32) | (Math.max(x, y) & 0xffffffffL);
        return losCache.computeIfAbsent(key, k -> a.hasLineOfSight(b));
    }

    private static LivingEntity nearest(LivingEntity e) {
        double r = DataStack.get().cfg().mobRadius();
        var type = e.getType();
        String sig = null;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        var loc = e.getLocation();
        for (Mob m : e.getWorld().getNearbyEntitiesByType(Mob.class, loc, r)) {
            if (m == e || m.getType() != type) continue; // cheap reject before any signature
            double d = m.getLocation().distanceSquared(loc);
            if (d >= bestD || !m.isValid() || !eligible(m)) continue;
            if (sig == null) sig = sig(e);
            if (!sig.equals(sig(m))) continue;
            if (!sees(e, m)) continue; // no merging through walls
            bestD = d;
            best = m;
        }
        return best == null || eligCache == null || MobSignature.eligible(best) ? best : null;
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
        ticksSinceRefill++;
        sigCache = new IdentityHashMap<>();
        eligCache = new IdentityHashMap<>();
        losCache = new java.util.HashMap<>();
        try {
            int budget = MOBS_PER_TICK, chunks = 0;
            while (budget > 0) {
                if (pendingIdx >= pending.length) {
                    // load the next chunk; bound empty-chunk skipping too
                    if (++chunks > 64) break;
                    if (queue.isEmpty()) {
                        if (ticksSinceRefill < MIN_PASS_TICKS) break;
                        refill();
                        ticksSinceRefill = 0;
                    }
                    if (queue.isEmpty()) break;
                    long[] c = queue.poll();
                    World w = Bukkit.getWorlds().size() > c[0] ? Bukkit.getWorlds().get((int) c[0]) : null;
                    pending = w == null || !w.isChunkLoaded((int) c[1], (int) c[2]) ? new Entity[0]
                        : w.getChunkAt((int) c[1], (int) c[2]).getEntities();
                    pendingIdx = 0;
                    continue;
                }
                Entity en = pending[pendingIdx++];
                if (en instanceof Mob m && m.isValid()) {
                    budget--;
                    MobInteractListener.checkGrowth(m);
                    if (m.isValid()) tryMerge(m);
                }
            }
        } finally {
            sigCache = null;
            eligCache = null;
            losCache = null;
        }
    }

    private void refill() {
        var worlds = Bukkit.getWorlds();
        for (int wi = 0; wi < worlds.size(); wi++)
            for (Chunk ch : worlds.get(wi).getLoadedChunks())
                queue.add(new long[] {wi, ch.getX(), ch.getZ()});
    }
}
