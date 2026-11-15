package th.park.datastack.mob;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import th.park.datastack.StackCount;

/**
 * Spec 9: natural spawns see a stack of N as N mobs. Spec 7b: a despawning stack loses one member.
 */
public final class SpawnCapListener implements Listener {
    static final double RANGE = 128.0;
    /** Per-player category sums are rescanned at most this often (natural spawning asks many times per tick). */
    private static final int CACHE_TICKS = 20;

    private record Cache(int tick, Map<SpawnCategory, Long> sums) {}
    private final Map<UUID, Cache> cache = new HashMap<>();

    // ---- spec 9: mob cap ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreSpawn(PreCreatureSpawnEvent e) {
        if (e.getReason() != SpawnReason.NATURAL) return; // chunk-gen animals are one-time, not cap-driven
        SpawnCategory cat = e.getType().getSpawnCategory();
        if (cat == null || cat == SpawnCategory.MISC) return;
        Location loc = e.getSpawnLocation();
        World w = loc.getWorld();
        int limit;
        try { limit = w.getSpawnLimit(cat); } catch (IllegalArgumentException ex) { return; }
        if (limit <= 0) return;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        int inRange = 0;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(loc);
            if (d <= RANGE * RANGE) inRange++;
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest == null) return;
        Map<SpawnCategory, Long> sums = sums(nearest);
        long sum = sums.getOrDefault(cat, 0L);
        if (!SpawnBudget.allows(sum, limit, inRange)) {
            e.setCancelled(true);
            e.setShouldAbortSpawn(true); // stop this pack attempt instead of retrying nearby spots
        } else {
            sums.put(cat, sum + 1); // count it now so a burst within one cache window cannot overshoot
        }
    }

    private Map<SpawnCategory, Long> sums(Player p) {
        int now = Bukkit.getCurrentTick();
        Cache c = cache.get(p.getUniqueId());
        if (c != null && now - c.tick < CACHE_TICKS) return c.sums;
        Map<SpawnCategory, Long> sums = new EnumMap<>(SpawnCategory.class);
        for (LivingEntity le : p.getWorld().getNearbyLivingEntities(p.getLocation(), RANGE)) {
            if (le instanceof Player || !SpawnBudget.countsForCap(le.getRemoveWhenFarAway())) continue;
            sums.merge(le.getSpawnCategory(), StackCount.get(le), Long::sum);
        }
        cache.put(p.getUniqueId(), new Cache(now, sums));
        return sums;
    }

    // ---- hard despawn range from Paper's world config (entities.spawning.despawn-ranges.<cat>.hard) ----

    private final Map<String, Map<SpawnCategory, Double>> hardRanges = new HashMap<>();

    private double hardRange(World w, SpawnCategory cat) {
        return hardRanges.computeIfAbsent(w.getName(), n -> new EnumMap<>(SpawnCategory.class))
            .computeIfAbsent(cat, c -> readHardRange(w, c));
    }

    /** World file overrides the defaults file; "default", missing or unreadable means 128. Read once per world+category. */
    private static double readHardRange(World w, SpawnCategory cat) {
        String path = "entities.spawning.despawn-ranges." + SpawnBudget.paperCategoryKey(cat.name()) + ".hard";
        Object v = null;
        try {
            File world = new File(w.getWorldFolder(), "paper-world.yml");
            if (world.isFile()) v = YamlConfiguration.loadConfiguration(world).get(path);
            if (v == null || "default".equals(v)) {
                File defs = new File(Bukkit.getWorldContainer(), "config/paper-world-defaults.yml");
                if (defs.isFile()) v = YamlConfiguration.loadConfiguration(defs).get(path);
            }
        } catch (RuntimeException ignored) {}
        return SpawnBudget.hardRange(v);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { cache.remove(e.getPlayer().getUniqueId()); }

    // ---- spec 7b: despawn one at a time ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (e.getCause() != EntityRemoveEvent.Cause.DESPAWN) return; // not UNLOAD, DEATH, PLUGIN, DISCARD (/kill), ...
        if (!(e.getEntity() instanceof LivingEntity le) || le instanceof Player) return;
        long count = StackCount.get(le);
        if (count <= 1 || le.getPersistentDataContainer().has(MobDeathListener.DYING, PersistentDataType.BYTE)) return;
        World w = le.getWorld();
        boolean peaceful = w.getDifficulty() == Difficulty.PEACEFUL && le instanceof Enemy;
        double best = Double.POSITIVE_INFINITY;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            best = Math.min(best, p.getLocation().distanceSquared(le.getLocation()));
        }
        if (!SpawnBudget.despawnKeepsRest(best, hardRange(w, le.getSpawnCategory()), peaceful)) return;
        // no gear, no rider, no leash (and no lead drop); fresh entity => vanilla's 30 s idle timer restarts
        MobInteractListener.splitCopy(le, count - 1);
    }
}
