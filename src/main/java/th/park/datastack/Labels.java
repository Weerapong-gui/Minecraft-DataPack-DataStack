package th.park.datastack;

/** Pure label formatting, kept free of Bukkit so it is unit-testable. */
public final class Labels {
    private Labels() {}

    /** Returns "xN" when count exceeds threshold, otherwise null (no label). */
    public static String text(long count, int threshold) {
        return count > threshold ? "x" + count : null;
    }

    /** True if a plain-text name has the shape of a plugin label: "xN", optionally after "<name> ". */
    public static boolean isOurs(String plainName) {
        return plainName != null && plainName.matches("(.+ )?x\\d+");
    }

    /** Whether an entity gets a label: dropped items always (their name tells what it is), mobs only as a stack. */
    public static boolean shown(boolean item, long count) {
        return item || count > 1;
    }
}
