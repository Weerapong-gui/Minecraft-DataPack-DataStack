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
}
