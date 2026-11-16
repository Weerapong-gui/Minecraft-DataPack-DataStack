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
        return of(damageCauseName, false);
    }

    /**
     * As {@link #of(String)}, but burning set by an entity (Fire Aspect, Flame arrow, a mob's fire)
     * or with a recent player killer counts as an attack: it kills one member (spec rule 4).
     */
    public static Outcome of(String damageCauseName, boolean entityIgnitedOrPlayerKill) {
        if (entityIgnitedOrPlayerKill && ("FIRE".equals(damageCauseName) || "FIRE_TICK".equals(damageCauseName)))
            return Outcome.ONE;
        return damageCauseName != null && ENVIRONMENT.contains(damageCauseName) ? Outcome.WHOLE_STACK : Outcome.ONE;
    }
}
