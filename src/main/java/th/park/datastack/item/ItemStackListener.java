package th.park.datastack.item;

import org.bukkit.Bukkit;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import th.park.datastack.DataStack;
import th.park.datastack.Keys;
import th.park.datastack.StackCount;

/**
 * Dropped-item stacking. Invariant: the PDC count exists only when the true count exceeds the item's
 * max stack size; the ItemStack amount on the entity is always min(count, maxStack). Small stacks are
 * left entirely to vanilla.
 */
public final class ItemStackListener implements Listener {
    private final Plugin plugin;

    public ItemStackListener(Plugin plugin) { this.plugin = plugin; }

    static int max(Item item) { return item.getItemStack().getMaxStackSize(); }

    public static long count(Item item) {
        Long v = item.getPersistentDataContainer().get(Keys.COUNT, PersistentDataType.LONG);
        return v != null ? v : item.getItemStack().getAmount();
    }

    public static void setCount(Item item, long n) {
        if (n <= 0) { item.remove(); return; }
        int max = max(item);
        ItemStack s = item.getItemStack();
        s.setAmount((int) Math.min(n, max));
        item.setItemStack(s);
        StackCount.set(item, n); // refreshes / clears the xN label
        if (n <= max) item.getPersistentDataContainer().remove(Keys.COUNT);
    }

    /**
     * Despawn age (the NBT "Age"), which differs from ticksLived for summoned/reloaded items. The API has
     * no getter, so read ItemEntity.getAge() reflectively; fall back to ticksLived if that ever breaks.
     */
    static int age(Item item) {
        try {
            Object handle = item.getClass().getMethod("getHandle").invoke(item);
            return (int) handle.getClass().getMethod("getAge").invoke(handle);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return item.getTicksLived();
        }
    }

    private static boolean stacked(Item item) { return count(item) > max(item); }

    /** Moves source into target if they are identical and the cap allows; returns true on merge. */
    private static boolean merge(Item source, Item target) {
        if (source == target || !source.isValid() || !target.isValid()) return false;
        if (!target.getItemStack().isSimilar(source.getItemStack())) return false;
        long sum = count(source) + count(target);
        if (sum > DataStack.get().cfg().maxItemStack()) return false;
        int age = Math.max(age(source), age(target)); // despawn follows the oldest
        source.remove();
        setCount(target, sum);
        target.setTicksLived(Math.max(1, age)); // CraftItem.setTicksLived also writes the despawn age
        return true;
    }

    /** Vanilla only merges partial stacks; take it over so counts beyond 64 add up correctly. */
    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        if (!e.getTarget().getItemStack().isSimilar(e.getEntity().getItemStack())) return;
        e.setCancelled(true);
        merge(e.getEntity(), e.getTarget());
    }

    /**
     * A new item looks for the biggest identical pile within pull-radius; the bigger of the two flies
     * into the smaller one and merges on arrival (also covers full stacks, which vanilla never merges).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        Item spawned = e.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!spawned.isValid()) return;
            double r = DataStack.get().cfg().pullRadius();
            Item best = null;
            for (Entity n : spawned.getNearbyEntities(r, r, r)) {
                if (!(n instanceof Item other) || !other.isValid()) continue;
                if (!other.getItemStack().isSimilar(spawned.getItemStack())) continue;
                if (best == null || count(other) > count(best)) best = other;
            }
            if (best == null || count(spawned) + count(best) > DataStack.get().cfg().maxItemStack()) {
                setCount(spawned, count(spawned)); // label a fresh pile
                return;
            }
            if (count(spawned) > count(best)) pull(spawned, best);
            else pull(best, spawned);
        });
    }

    private static final int PULL_TICKS = 40;
    private static final double PULL_SPEED = 0.35, ARRIVE = 0.7;

    /** Steers the mover toward the target each tick, then merges; gives up quietly if either one goes away. */
    private void pull(Item mover, Item target) {
        mover.setPickupDelay(Math.max(mover.getPickupDelay(), 10)); // nobody grabs it mid-flight
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!mover.isValid() || !target.isValid() || mover.getWorld() != target.getWorld()) { cancel(); return; }
                org.bukkit.util.Vector d = target.getLocation().toVector().subtract(mover.getLocation().toVector());
                if (d.lengthSquared() < ARRIVE * ARRIVE || ++t >= PULL_TICKS) {
                    if (!merge(mover, target)) setCount(mover, count(mover));
                    cancel();
                    return;
                }
                mover.setVelocity(d.normalize().multiply(PULL_SPEED).add(new org.bukkit.util.Vector(0, 0.04, 0)));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickup(PlayerAttemptPickupItemEvent e) {
        Item item = e.getItem();
        if (!stacked(item)) { refreshLater(item); return; } // vanilla is exact for normal stacks
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (item.getPickupDelay() > 0) return;
        if (item.getOwner() != null && !item.getOwner().equals(p.getUniqueId())) return;
        ItemStack proto = item.getItemStack();
        int max = proto.getMaxStackSize();
        PlayerInventory inv = p.getInventory();
        int free = 0, partial = 0;
        ItemStack[] slots = inv.getStorageContents(); // hotbar + main, what addItem fills
        for (ItemStack s : slots) {
            if (s == null || s.getType().isAir()) free++;
            else if (s.isSimilar(proto)) partial += Math.max(0, max - s.getAmount());
        }
        long count = count(item);
        long take = PickupMath.take(count, max, free, partial);
        if (take <= 0) return;
        long given = give(inv, proto, take, max);
        if (given <= 0) return;
        p.playPickupItemAnimation(item, (int) Math.min(given, max));
        setCount(item, count - given);
    }

    /** Vanilla may take part of a small pile; relabel from its new amount once it has. */
    private void refreshLater(Item item) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (item.isValid()) setCount(item, item.getItemStack().getAmount());
        });
    }

    /** Adds n copies in stack-sized chunks; returns how many actually went in (leftovers never vanish). */
    private static long give(Inventory inv, ItemStack proto, long n, int max) {
        long given = 0;
        while (given < n) {
            int chunk = (int) Math.min(max, n - given);
            ItemStack c = proto.clone();
            c.setAmount(chunk);
            int left = 0;
            for (ItemStack l : inv.addItem(c).values()) left += l.getAmount();
            given += chunk - left;
            if (left > 0) break;
        }
        return given;
    }

    /** Mobs (allays, foxes, piglins...) may not take oversized stacks: simplest no-dupe rule. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) return; // handled via PlayerAttemptPickupItemEvent
        if (stacked(e.getItem())) e.setCancelled(true);
        else refreshLater(e.getItem());
    }

    /** Hoppers / hopper minecarts: one stack's worth per transfer, never more than exists. */
    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        Item item = e.getItem();
        if (!stacked(item)) { refreshLater(item); return; }
        e.setCancelled(true);
        Inventory inv = e.getInventory();
        ItemStack proto = item.getItemStack();
        int max = Math.min(proto.getMaxStackSize(), inv.getMaxStackSize());
        int free = 0, partial = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) free++;
            else if (s.isSimilar(proto)) partial += Math.max(0, max - s.getAmount());
        }
        long count = count(item);
        long move = PickupMath.hopperMove(count, max, free, partial);
        if (move <= 0) return;
        long given = give(inv, proto, move, max);
        if (given <= 0) return;
        setCount(item, count - given);
        // cancelled pickups get no cooldown; apply vanilla's 8-tick one so the rate stays normal
        if (inv.getHolder(false) instanceof Hopper h) h.setTransferCooldown(8);
    }
}
