package th.park.datastack.mob;

/** Pure rules for mob cap accounting (spec 9) and one-at-a-time despawn (spec 7b). */
public final class SpawnBudget {
    private SpawnBudget() {}

    /** Simplified per-player cap: a stack of N counts as N against {@code categoryLimit} per player in range. */
    public static boolean allows(long summedCountNearby, int categoryLimit, int playersInRange) {
        return summedCountNearby < (long) categoryLimit * Math.max(1, playersInRange);
    }

    /**
     * Whether a despawning stack should leave its other members behind. Only vanilla's random
     * despawn (a player within the hard range) takes one member; the instant despawn beyond the hard
     * range, no player in the world, or the peaceful purge of monsters removes the whole stack, as
     * vanilla would remove every one of N separate mobs at once.
     */
    public static boolean despawnKeepsRest(double nearestPlayerDistSq, double hardRange, boolean peacefulPurge) {
        return !peacefulPurge && nearestPlayerDistSq <= hardRange * hardRange;
    }
}
