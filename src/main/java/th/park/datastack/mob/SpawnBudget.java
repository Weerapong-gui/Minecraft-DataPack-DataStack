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

    public static final double DEFAULT_HARD_RANGE = 128.0;

    /** Vanilla's mob cap skips mobs that never despawn (name-tagged, picked up items, persistent). */
    public static boolean countsForCap(boolean removeWhenFarAway) {
        return removeWhenFarAway;
    }

    /** Paper world-config key under entities.spawning.despawn-ranges for a Bukkit SpawnCategory name. */
    public static String paperCategoryKey(String bukkitCategory) {
        return switch (bukkitCategory) {
            case "MONSTER" -> "monster";
            case "ANIMAL" -> "creature";
            case "AMBIENT" -> "ambient";
            case "AXOLOTL" -> "axolotls";
            case "WATER_ANIMAL" -> "water_creature";
            case "WATER_AMBIENT" -> "water_ambient";
            case "WATER_UNDERGROUND_CREATURE" -> "underground_water_creature";
            default -> "misc";
        };
    }

    /** A configured hard range: a positive number is used, anything else ("default", missing) means 128. */
    public static double hardRange(Object configured) {
        if (configured instanceof Number n && n.doubleValue() > 0) return n.doubleValue();
        if (configured instanceof String str) {
            try { double d = Double.parseDouble(str.trim()); if (d > 0) return d; } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_HARD_RANGE;
    }
}
