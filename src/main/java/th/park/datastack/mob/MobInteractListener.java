package th.park.datastack.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.destroystokyo.paper.event.entity.EntityZapEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockShearEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import th.park.datastack.Keys;
import th.park.datastack.StackCount;

/**
 * Player and world interactions with a stack (spec rules 5, 6, 8, 10): name tags split one member
 * off, shearing and transforms affect exactly one member, growth splits one adult off a baby stack.
 * Milking and breeding need no code: vanilla acts on the single front entity (one bucket, one pair,
 * love-mode cooldown on that entity) and the bred baby is merged by MobMerger's spawn hook.
 */
public final class MobInteractListener implements Listener {
    /** Set on a stack while it is a baby stack, so the sweep can tell when its front member grew up. */
    public static final NamespacedKey BABY = new NamespacedKey("datastack", "baby");
    /** A lightning bolt strikes for several ticks: the fresh stack copy must not be converted again. */
    private static final long LIGHTNING_IMMUNE_MS = 2000;

    private static MobInteractListener instance;
    private final Plugin plugin;
    private final Map<UUID, Long> lightningImmune = new HashMap<>();

    public MobInteractListener(Plugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static void clear() { instance = null; }

    // ---- rule 5: name tag ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNameTag(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof LivingEntity stack)) return;
        Player p = e.getPlayer();
        ItemStack tag = p.getInventory().getItem(e.getHand());
        if (tag == null || tag.getType() != Material.NAME_TAG) return;
        Component name = tag.hasItemMeta() ? tag.getItemMeta().customName() : null;
        long count = StackCount.get(stack);
        if (!InteractPolicy.nameTagSplits(count, name != null)) return;
        e.setCancelled(true);
        LivingEntity one = splitCopy(stack, 1);
        if (one == null) return;
        StackCount.set(stack, count - 1);
        one.customName(name);
        one.setRemoveWhenFarAway(false); // vanilla name tags make the mob persistent
        if (p.getGameMode() != GameMode.CREATIVE) tag.setAmount(tag.getAmount() - 1);
        p.swingHand(e.getHand());
    }

    // ---- rule 10: shearing (milking is vanilla: one bucket) ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) { afterShear(e.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockShear(BlockShearEntityEvent e) { afterShear(e.getEntity()); }

    /** Vanilla shears the stack entity (one yield); next tick that sheared member leaves the stack. */
    private void afterShear(Entity en) {
        if (!(en instanceof Sheep sheep) || StackCount.get(sheep) <= 1 || sheep.isSheared()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            long n = StackCount.get(sheep);
            if (!sheep.isValid() || n <= 1 || !sheep.isSheared()) return;
            if (splitCopy(sheep, 1) == null) return; // the copy is the sheared one
            sheep.setSheared(false);
            StackCount.set(sheep, n - 1);
        });
    }

    // ---- rule 6: transforms ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) { transformOne(e); }

    /** Lightning conversions (pig, villager) fire this subclass on its own handler list, not EntityTransformEvent's. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onZap(EntityZapEvent e) { transformOne(e); }

    private void transformOne(EntityTransformEvent e) {
        if (!(e.getEntity() instanceof LivingEntity src)) return;
        String reason = e.getTransformReason().name();
        long count = StackCount.get(src);
        var srcPdc = src.getPersistentDataContainer();
        if (src.isDead() || srcPdc.has(MobDeathListener.DYING, PersistentDataType.BYTE)) {
            // e.g. INFECTION: the villager died first and MobDeathListener normally already spawned the
            // count-1 replacement; only the converted body must not carry the stack's count or label
            if (count > 1) {
                Component lbl = src.customName();
                if (!srcPdc.has(MobDeathListener.DYING, PersistentDataType.BYTE)) {
                    // the death listener did not run (no EntityDeathEvent): keep the rest of the stack here
                    srcPdc.set(MobDeathListener.DYING, PersistentDataType.BYTE, (byte) 1);
                    var max = src.getAttribute(Attribute.MAX_HEALTH);
                    MobDeathListener.spawnReplacement(src, count - 1, max == null ? 20.0 : max.getValue());
                }
                for (Entity t : e.getTransformedEntities()) clearStackData(t, lbl);
            }
            return;
        }
        if (!InteractPolicy.transformOne(count, reason)) return;
        // the rest of the stack stays as the old kind; the source transforms as a single member
        Component srcLabel = src.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE) ? src.customName() : null;
        // a tadpole loaded with a frog-age turns into a frog while being copied: zero the age first
        // (the source is already mid-conversion, so this does not stop it)
        if (src instanceof org.bukkit.entity.Tadpole tp) tp.setAge(0);
        LivingEntity rest = splitCopy(src, count - 1);
        if (rest == null) return; // leave vanilla alone rather than lose members
        resetConversion(rest);
        if (reason.equals("LIGHTNING")) immunize(rest.getUniqueId());
        StackCount.set(src, 1); // also drops the xN label, so the new entity does not inherit it
        // until the fold-back below, the sweep must not merge src into rest (that would be N+1)
        srcPdc.set(MobDeathListener.DYING, PersistentDataType.BYTE, (byte) 1);
        for (Entity t : e.getTransformedEntities()) clearStackData(t, srcLabel);
        Bukkit.getScheduler().runTask(plugin, () -> {
            // a transform that keeps its source would leave one extra old-kind member: fold it back
            if (src.isValid() && !src.isDead() && rest.isValid()) {
                src.remove();
            }
            for (Entity t : e.getTransformedEntities())
                if (t instanceof LivingEntity le && le.isValid()) MobMerger.tryMerge(le);
        });
    }

    /** Cancels a repeat lightning conversion of a stack copy made by this bolt. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLightningRepeat(EntityTransformEvent e) {
        if (e.getTransformReason() == EntityTransformEvent.TransformReason.LIGHTNING && immune(e.getEntity().getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onZapRepeat(EntityZapEvent e) {
        if (immune(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    /** A cancelled conversion falls back to lightning damage and fire, which would burn the whole stack. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLightningDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LIGHTNING && immune(e.getEntity().getUniqueId()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLightningFire(org.bukkit.event.entity.EntityCombustByEntityEvent e) {
        if (e.getCombuster() instanceof org.bukkit.entity.LightningStrike && immune(e.getEntity().getUniqueId()))
            e.setCancelled(true);
    }

    private void immunize(UUID id) {
        long now = System.currentTimeMillis();
        lightningImmune.values().removeIf(until -> until < now); // purge expired on each insert
        lightningImmune.put(id, now + LIGHTNING_IMMUNE_MS);
    }

    /**
     * The copy inherits the source's in-progress conversion (cure timer, drowning, freezing,
     * overworld zombification, tadpole age); without a reset it would convert next tick and the
     * whole stack would transform one by one.
     */
    static void resetConversion(LivingEntity r) {
        if (r instanceof org.bukkit.entity.Zombie z) z.setConversionTime(-1); // incl. zombie villager cure, husk
        if (r instanceof org.bukkit.entity.Skeleton sk) sk.setConversionTime(-1);
        if (r instanceof org.bukkit.entity.PiglinAbstract pa) pa.setConversionTime(-1);
        if (r instanceof org.bukkit.entity.Hoglin h) h.setConversionTime(-1);
        if (r instanceof org.bukkit.entity.Tadpole t) t.setAge(0);
    }

    private boolean immune(UUID id) {
        Long until = lightningImmune.get(id);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) { lightningImmune.remove(id); return false; }
        return true;
    }

    /** The new entity was built before this event, copying the source's xN label (and maybe its PDC). */
    private static void clearStackData(Entity t, Component srcLabel) {
        var pdc = t.getPersistentDataContainer();
        if (pdc.has(Keys.LABEL, PersistentDataType.BYTE) || (srcLabel != null && srcLabel.equals(t.customName()))) {
            t.customName(null);
            t.setCustomNameVisible(false);
        }
        pdc.remove(Keys.LABEL);
        pdc.remove(Keys.COUNT);
        pdc.remove(BABY);
        pdc.remove(MobDeathListener.DYING);
    }

    // ---- rule 6: baby -> adult (no grow event in Paper 26.3; checked from the chunk sweep) ----

    /** Called by the merge sweep for each mob it visits. Cheap: a PDC read and an age check. */
    public static void checkGrowth(LivingEntity e) {
        if (!(e instanceof Ageable a)) return;
        long n = StackCount.get(e);
        var pdc = e.getPersistentDataContainer();
        boolean wasBaby = pdc.has(BABY, PersistentDataType.BYTE);
        if (n <= 1) { if (wasBaby) pdc.remove(BABY); return; }
        if (!a.isAdult()) { if (!wasBaby) pdc.set(BABY, PersistentDataType.BYTE, (byte) 1); return; }
        pdc.remove(BABY);
        if (!InteractPolicy.grewUp(n, wasBaby, true)) return;
        LivingEntity rest = splitCopy(e, n - 1);
        if (rest == null) return;
        ((Ageable) rest).setBaby(); // the stack stays baby with its age reset
        rest.getPersistentDataContainer().set(BABY, PersistentDataType.BYTE, (byte) 1);
        StackCount.set(e, 1); // the grown one leaves as a single adult
        MobMerger.tryMerge(e);
    }

    /** Feeding a stack: the fed one leaves as a single animal in love; the rest stay stacked, not in love. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLove(org.bukkit.event.entity.EntityEnterLoveModeEvent e) {
        org.bukkit.entity.Animals fed = e.getEntity();
        long n = StackCount.get(fed);
        if (n <= 1) return;
        LivingEntity rest = splitCopy(fed, n - 1);
        if (rest == null) return;
        ((org.bukkit.entity.Animals) rest).setLoveModeTicks(0);
        StackCount.set(fed, 1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (rest.isValid()) ((org.bukkit.entity.Animals) rest).setLoveModeTicks(0);
        });
    }

    /**
     * A copy of a living stack holding {@code count} members: same type and data and health,
     * no equipment (zero drop chances), no riders, no vehicle, no leash. Source is untouched.
     */
    static LivingEntity splitCopy(LivingEntity src, long count) {
        Entity copy = src.copy(src.getLocation());
        if (!(copy instanceof LivingEntity r)) {
            if (copy != null) copy.remove();
            return null;
        }
        r.getPersistentDataContainer().remove(MobDeathListener.DYING);
        for (Entity p : r.getPassengers()) { r.removePassenger(p); p.remove(); }
        r.leaveVehicle();
        if (r.isLeashed()) r.setLeashHolder(null);
        EntityEquipment eq = r.getEquipment();
        if (eq != null) {
            eq.clear();
            for (EquipmentSlot s : EquipmentSlot.values()) if (r.canUseEquipmentSlot(s)) eq.setDropChance(s, 0f);
        }
        var max = r.getAttribute(Attribute.MAX_HEALTH);
        r.setHealth(Math.max(1.0, Math.min(max == null ? 20.0 : max.getValue(), src.getHealth())));
        StackCount.set(r, count);
        return r;
    }
}
