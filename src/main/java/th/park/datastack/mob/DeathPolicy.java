package th.park.datastack.mob;

import java.util.Set;

/** Decides whether a damage cause kills one stack member or the whole stack (spec rules 4, 7). */
public final class DeathPolicy {
    private DeathPolicy() {}

    public enum Outcome { ONE, WHOLE_STACK }

    private static final Set<String> ENVIRONMENT = Set.of(
        "LAVA", "FIRE", "FIRE_TICK", "DROWNING", "FALL", "SUFFOCATION", "VOID",
        "HOT_FLOOR", "FREEZE", "STARVATION", "CONTACT", "LIGHTNING", "MELTING");

    /** Takes the DamageCause name; anything unknown kills one at a time. */
    public static Outcome of(String damageCauseName) {
        return damageCauseName != null && ENVIRONMENT.contains(damageCauseName) ? Outcome.WHOLE_STACK : Outcome.ONE;
    }
}
