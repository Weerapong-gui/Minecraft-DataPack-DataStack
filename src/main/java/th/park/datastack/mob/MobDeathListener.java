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

    /** Health of each stacked mob just before the latest damage (spec 7a). */
    private final Map<UUID, Double> preHitHealth = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof LivingEntity le && StackCount.get(le) > 1)
            preHitHealth.put(le.getUniqueId(), le.getHealth());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Double before = preHitHealth.remove(dead.getUniqueId());
        long count = StackCount.get(dead);
        if (count <= 1) return;
        var pdc = dead.getPersistentDataContainer();
        if (pdc.has(DYING, PersistentDataType.BYTE)) return;
        pdc.set(DYING, PersistentDataType.BYTE, (byte) 1);

        EntityDamageEvent last = dead.getLastDamageCause();
        String cause = last == null ? null : last.getCause().name();
        if (DeathPolicy.of(cause) == DeathPolicy.Outcome.WHOLE_STACK) {
            multiplyDrops(e, dead, count);
        } else {
            double hp = before == null ? maxHealth(dead) : before;
            spawnReplacement(dead, count - 1, hp);
        }
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
     * Returns null if the spawn failed. Only effect besides the spawn: none on {@code dead}.
     */
    public static LivingEntity spawnReplacement(LivingEntity dead, long newCount, double health) {
        Entity copy = dead.copy(dead.getLocation());
        if (!(copy instanceof LivingEntity r)) {
            if (copy != null) copy.remove();
            return null;
        }
        r.getPersistentDataContainer().remove(DYING);
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
