package th.park.datastack;

import org.bukkit.NamespacedKey;

public final class Keys {
    private Keys() {}

    /** Stack size stored on a stacked entity (long). */
    public static final NamespacedKey COUNT = new NamespacedKey("datastack", "count");
    /** Marks that the entity's custom name is a plugin-owned xN label (byte 1). */
    public static final NamespacedKey LABEL = new NamespacedKey("datastack", "label");
}
