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

    private static void refreshLabel(Entity e, long count) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        boolean marked = pdc.has(Keys.LABEL, PersistentDataType.BYTE);
        String plain = e.customName() == null ? null
            : PlainTextComponentSerializer.plainText().serialize(e.customName());
        boolean ours = marked && Labels.isOurs(plain);
        if (marked && !ours) pdc.remove(Keys.LABEL); // renamed by a player: name is theirs now
        int threshold = 1; // items and mobs: any count above one shows xN
        String text = Labels.text(count, threshold);
        if (text != null) {
            // do not overwrite a player-given name
            if (e.customName() != null && !ours) return;
            Component name = e instanceof org.bukkit.entity.Item item ? item.getItemStack().effectiveName()
                : Component.translatable(e.getType().translationKey());
            e.customName(Component.text().append(name.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" " + text, NamedTextColor.YELLOW, TextDecoration.BOLD)).build());
            e.setCustomNameVisible(true);
            pdc.set(Keys.LABEL, PersistentDataType.BYTE, (byte) 1);
        } else if (ours) {
            e.customName(null);
            e.setCustomNameVisible(false);
            pdc.remove(Keys.LABEL);
        }
    }

    /** Rewrites an existing plugin label in the current format (labels made by older versions). */
    public static void relabel(Entity e) {
        if (!e.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE)) return;
        if (e instanceof org.bukkit.entity.Item item) th.park.datastack.item.ItemStackListener.setCount(item, th.park.datastack.item.ItemStackListener.count(item));
        else set(e, get(e));
    }
}
