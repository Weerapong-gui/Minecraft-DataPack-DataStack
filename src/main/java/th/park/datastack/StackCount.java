package th.park.datastack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class StackCount {
    private StackCount() {}

    public static long get(Entity e) {
        Long v = e.getPersistentDataContainer().get(Keys.COUNT, PersistentDataType.LONG);
        return v == null ? 1 : v;
    }

    /** Stores the count and refreshes the xN label; count <= 0 removes the entity. */
    public static void set(Entity e, long count) {
        if (count <= 0) {
            e.remove();
            return;
        }
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.COUNT, PersistentDataType.LONG, count);
        refreshLabel(e, count);
    }

    /** True if the entity's current name is a label this plugin wrote (not a player-given name). */
    public static boolean hasOurLabel(Entity e) {
        if (!e.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE)) return false;
        if (e.customName() == null) return false;
        if (e instanceof org.bukkit.entity.Item) return true; // dropped items cannot be renamed in the world
        String plain = PlainTextComponentSerializer.plainText().serialize(e.customName());
        return Labels.isOurs(plain) || plain.equals(e.getType().translationKey())
            || plain.startsWith(e.getType().translationKey() + " x");
    }

    private static void refreshLabel(Entity e, long count) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        boolean ours = hasOurLabel(e);
        if (!ours) {
            pdc.remove(Keys.LABEL);
            if (e.customName() != null) return; // a player-given name is never overwritten
        }
        Component name = e instanceof org.bukkit.entity.Item item ? item.getItemStack().effectiveName()
            : Component.translatable(e.getType().translationKey());
        var label = Component.text().append(name.colorIfAbsent(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
        if (count > 1) label.append(Component.text(" x" + count, NamedTextColor.YELLOW, TextDecoration.BOLD));
        e.customName(label.build());
        e.setCustomNameVisible(true);
        pdc.set(Keys.LABEL, PersistentDataType.BYTE, (byte) 1);
    }

    /** Labels a single mob or item that has never been labelled, and rewrites old-format labels. */
    public static void relabel(Entity e) {
        if (e instanceof org.bukkit.entity.Item item) {
            th.park.datastack.item.ItemStackListener.setCount(item, th.park.datastack.item.ItemStackListener.count(item));
        } else if (e instanceof org.bukkit.entity.Mob && (e.customName() == null || hasOurLabel(e))) {
            set(e, get(e));
        }
    }
}
