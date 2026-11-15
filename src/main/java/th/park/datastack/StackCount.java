package th.park.datastack;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
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
        boolean ours = pdc.has(Keys.LABEL, PersistentDataType.BYTE);
        int threshold = e instanceof Item item ? item.getItemStack().getMaxStackSize() : 1;
        String text = Labels.text(count, threshold);
        if (text != null) {
            // do not overwrite a player-given name
            if (e.customName() != null && !ours) return;
            e.customName(Component.text(text));
            e.setCustomNameVisible(true);
            pdc.set(Keys.LABEL, PersistentDataType.BYTE, (byte) 1);
        } else if (ours) {
            e.customName(null);
            e.setCustomNameVisible(false);
            pdc.remove(Keys.LABEL);
        }
    }
}
