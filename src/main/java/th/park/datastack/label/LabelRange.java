package th.park.datastack.label;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import th.park.datastack.Keys;

/**
 * Shows each stack label only to players within {@code range} blocks.
 * The label is an entity custom name, which the client draws through walls; the server can only toggle it
 * for everyone, so the per-player "name visible" flag (entity data index 3) is rewritten in outgoing packets.
 */
public final class LabelRange implements PacketListener, Listener {
    /** Entity data index of the "custom name visible" boolean, shared by every entity type. */
    private static final int NAME_VISIBLE = 3;
    /** Half-size of the scan box; must cover the entity tracking range so spawn packets are rewritten too. */
    private static final double SCAN = 64;
    private static final long PERIOD_TICKS = 5;

    private final double range;
    /** Entity ids currently carrying a plugin label near some player (read from the netty thread). */
    private final Set<Integer> labelled = ConcurrentHashMap.newKeySet();
    /** Per player: entity id -> label state last sent to that client. */
    private final Map<UUID, Map<Integer, Boolean>> shown = new ConcurrentHashMap<>();
    private PacketListenerCommon registered;
    private BukkitTask task;

    public LabelRange(double range) {
        this.range = range;
    }

    public void start(Plugin plugin) {
        registered = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (registered != null) PacketEvents.getAPI().getEventManager().unregisterListener(registered);
        HandlerList.unregisterAll(this);
        labelled.clear();
        shown.clear();
    }

    private static boolean isLabelled(Entity e) {
        return e.isCustomNameVisible() && e.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE);
    }

    private void tick() {
        Set<Integer> seen = new HashSet<>();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            Map<Integer, Boolean> state = shown.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
            Set<Integer> near = new HashSet<>();
            var eye = p.getLocation();
            for (Entity e : p.getNearbyEntities(SCAN, SCAN, SCAN)) {
                if (!isLabelled(e)) continue;
                int id = e.getEntityId();
                labelled.add(id);
                seen.add(id);
                near.add(id);
                boolean want = LabelVisibility.visible(eye.distanceSquared(e.getLocation()), range);
                Boolean had = state.put(id, want);
                if (had == null || had != want) send(p, id, want);
            }
            state.keySet().retainAll(near);
        }
        labelled.retainAll(seen);
    }

    private static void send(Player p, int entityId, boolean visible) {
        List<EntityData<?>> data = List.of(new EntityData<>(NAME_VISIBLE, EntityDataTypes.BOOLEAN, visible));
        PacketEvents.getAPI().getPlayerManager().sendPacket(p, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    @SuppressWarnings("unchecked")
    @Override public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;
        if (!(event.getPlayer() instanceof Player p)) return;
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int id = packet.getEntityId();
        if (!labelled.contains(id)) return;
        Map<Integer, Boolean> state = shown.get(p.getUniqueId());
        boolean want = state != null && Boolean.TRUE.equals(state.get(id));
        boolean changed = false;
        for (EntityData<?> d : packet.getEntityMetadata()) {
            if (d.getIndex() == NAME_VISIBLE && d.getValue() instanceof Boolean b && b != want) {
                ((EntityData<Boolean>) d).setValue(want);
                changed = true;
            }
        }
        if (changed) event.markForReEncode(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        shown.remove(e.getPlayer().getUniqueId());
    }
}
