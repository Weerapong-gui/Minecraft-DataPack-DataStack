package th.park.datastack.mob;

/** Pure decisions for stack interactions, kept free of Bukkit so they are unit-testable. */
public final class InteractPolicy {
    private InteractPolicy() {}

    /** A name tag splits one member off only when it carries a name and the target is a real stack. */
    public static boolean nameTagSplits(long count, boolean tagHasName) {
        return count > 1 && tagHasName;
    }

    /**
     * True if only one member of a stack should transform (rule 6). SPLIT (slime/magma death split)
     * is a death, already handled one-at-a-time by the death listener, so it is left alone.
     */
    public static boolean transformOne(long count, String reasonName) {
        return count > 1 && !"SPLIT".equals(reasonName);
    }

    /** A stacked baby whose front member turned adult: split one adult off (rule 6, growth). */
    public static boolean grewUp(long count, boolean wasBaby, boolean isAdult) {
        return count > 1 && wasBaby && isAdult;
    }
}
