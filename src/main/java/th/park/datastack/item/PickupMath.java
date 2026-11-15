package th.park.datastack.item;

/** Pure pickup arithmetic, free of Bukkit so it is unit-testable. */
public final class PickupMath {
    private PickupMath() {}

    /** How many of {@code count} fit: empty slots hold a full stack each, plus room in partial matching slots. */
    public static long take(long count, int maxStack, int freeSlots, int partialSpace) {
        long room = (long) freeSlots * maxStack + partialSpace;
        return Math.max(0, Math.min(count, room));
    }

    /** One hopper transfer: like {@link #take} but never more than one normal stack per event. */
    public static long hopperMove(long count, int maxStack, int freeSlots, int partialSpace) {
        return Math.min(take(count, maxStack, freeSlots, partialSpace), maxStack);
    }
}
