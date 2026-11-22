package th.park.datastack.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import th.park.datastack.StackCount;

/** Stacked mob deaths: one at a time (with shared health) or the whole stack for environmental causes. */
public final class MobDeathListener implements Listener {
    /** Set on a dying stack member so it is handled once. */
    public static final NamespacedKey DYING = new NamespacedKey("datastack", "dying");

    /** Environment damage that the front mob survives lowers the whole stack's shared health (spec 7a). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || StackCount.get(le) <= 1) return;
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) return; // an attack: front mob only
        Long lit = entityLit.get(le.getUniqueId());
        boolean attackFire = lit != null && lit >= System.currentTimeMillis();
        if (DeathPolicy.of(e.getCause().name(), attackFire) != DeathPolicy.Outcome.WHOLE_STACK) return;
        if (e.getFinalDamage() >= le.getHealth() + le.getAbsorptionAmount()) return; // lethal: whole stack dies anyway
        StackHealth.lower(le, e.getFinalDamage());
    }

    /** Stacks set burning by an entity (not lightning): their fire death is an attack, kills one. */
    private static final Map<UUID, Long> entityLit = new HashMap<>();
    private static final long ENTITY_LIT_MS = 60_000;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombustByEntity(org.bukkit.event.entity.EntityCombustByEntityEvent e) {
        if (e.getCombuster() instanceof org.bukkit.entity.LightningStrike) return; // lightning is environment
        if (!(e.getEntity() instanceof LivingEntity le) || StackCount.get(le) <= 1) return;
        long now = System.currentTimeMillis();
        entityLit.values().removeIf(until -> until < now);
        entityLit.put(le.getUniqueId(), now + ENTITY_LIT_MS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent e) {
        entityLit.remove(e.getEntity().getUniqueId());
    }

    private static boolean wholeStack(LivingEntity dead) {
        EntityDamageEvent last = dead.getLastDamageCause();
        Long lit = entityLit.get(dead.getUniqueId());
        boolean attacked = dead.getKiller() != null || (lit != null && lit >= System.currentTimeMillis());
        return DeathPolicy.of(last == null ? null : last.getCause().name(), attacked) == DeathPolicy.Outcome.WHOLE_STACK;
    }

    /** Drop changes only; if a later listener cancels the death, the drops never happen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeathDrops(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        long count = StackCount.get(dead);
        if (count <= 1 || dead.getPersistentDataContainer().has(DYING, PersistentDataType.BYTE)) return;
        if (wholeStack(dead)) multiplyDrops(e, dead, count);
    }

    /** Runs once the death is final (not cancelled), so a replacement can never dupe a surviving mob. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        long count = StackCount.get(dead);
        if (count <= 1) return;
        var pdc = dead.getPersistentDataContainer();
        if (pdc.has(DYING, PersistentDataType.BYTE)) return;
        pdc.set(DYING, PersistentDataType.BYTE, (byte) 1);
        if (!wholeStack(dead)) spawnReplacement(dead, count - 1, StackHealth.next(dead));
    }

    private static void multiplyDrops(EntityDeathEvent e, LivingEntity dead, long count) {
        List<ItemStack> gear = new ArrayList<>();
        EntityEquipment eq = dead.getEquipment();
        if (eq != null) for (EquipmentSlot s : EquipmentSlot.values()) {
            if (!dead.canUseEquipmentSlot(s)) continue;
            ItemStack it = eq.getItem(s);
            if (it != null && !it.isEmpty()) gear.add(it);
        }
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : e.getDrops()) {
            if (removeMatch(gear, drop)) continue; // equipment drops only once (spec 11)
            long total = (long) drop.getAmount() * (count - 1);
            int max = Math.max(1, drop.getMaxStackSize());
            while (total > 0) {
                int n = (int) Math.min(max, total);
                ItemStack c = drop.clone();
                c.setAmount(n);
                extra.add(c);
                total -= n;
            }
        }
        e.getDrops().addAll(extra);
        e.setDroppedExp((int) Math.min(Integer.MAX_VALUE, (long) e.getDroppedExp() * count));
    }

    private static boolean removeMatch(List<ItemStack> gear, ItemStack drop) {
        for (int i = 0; i < gear.size(); i++)
            if (gear.get(i).isSimilar(drop)) { gear.remove(i); return true; }
        return false;
    }

    private static double maxHealth(LivingEntity le) {
        var a = le.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    /**
     * Spawns the next stack member in place of {@code dead}: same type and data, no equipment
     * (and zero drop chances), the given health clamped to [1, max], and count {@code newCount}.
     * Returns null if the spawn failed. Only effect on {@code dead}: a leash is released (its lead drops once).
     */
    public static LivingEntity spawnReplacement(LivingEntity dead, long newCount, double health) {
        // unleash first so the lead drops once (from dead) and copy() carries no leash
        // (releasing a dead mob's leash drops nothing, so drop the lead here)
        if (dead.isLeashed()) {
            dead.setLeashHolder(null);
            dead.getWorld().dropItemNaturally(dead.getLocation(), new ItemStack(org.bukkit.Material.LEAD));
        }
        // copy unspawned and fix health first: a copy spawned with the dead mob's 0 health makes
        // clients start the death animation, which leaves the whole stack tinted red for good
        Entity copy = dead.copy();
        if (copy instanceof LivingEntity pre) {
            pre.setHealth(Math.max(1.0, Math.min(maxHealth(pre), health)));
            pre.getPersistentDataContainer().remove(DYING);
            if (!copy.spawnAt(dead.getLocation())) copy = null;
        }
        if (!(copy instanceof LivingEntity r)) {
            if (copy != null) copy.remove();
            org.bukkit.Bukkit.getLogger().warning("[DataStack] could not copy " + dead.getType() + " at "
                + dead.getLocation().toVector() + ": " + newCount + " stack members lost");
            return null;
        }
        r.getPersistentDataContainer().remove(DYING);
        // copy() carries riders and leash: drop both so they cannot be duplicated
        for (Entity p : r.getPassengers()) { r.removePassenger(p); p.remove(); }
        r.leaveVehicle();
        if (r.isLeashed()) r.setLeashHolder(null);
        EntityEquipment eq = r.getEquipment();
        if (eq != null) {
            eq.clear();
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (r.canUseEquipmentSlot(s)) eq.setDropChance(s, 0f);
            }
        }
        r.setFireTicks(0);
        r.setFallDistance(0);
        r.setVelocity(new org.bukkit.util.Vector());
        r.setNoDamageTicks(0);
        r.setHealth(Math.max(1.0, Math.min(maxHealth(r), health)));
        StackCount.set(r, newCount);
        return r;
    }
}
