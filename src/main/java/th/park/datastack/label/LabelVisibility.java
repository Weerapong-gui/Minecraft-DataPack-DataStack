package th.park.datastack.label;

/** Pure distance rule for showing a stack label to one player, kept free of Bukkit so it is unit-testable. */
public final class LabelVisibility {
    private LabelVisibility() {}

    /** True if a label at squared distance distSq should show; range <= 0 means always show. */
    public static boolean visible(double distSq, double range) {
        return range <= 0 || distSq <= range * range;
    }
}
