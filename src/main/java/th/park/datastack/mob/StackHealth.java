package th.park.datastack.mob;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import th.park.datastack.Keys;

/**
 * Spec 7a (revised): the stack keeps one shared health that only environment damage lowers
 * (a fall-damage XP farm leaves every member at half a heart). Player and mob attacks hurt the
 * front mob alone, so the next member comes in at the shared health, full if never lowered.
 */
public final class StackHealth {
    private StackHealth() {}

    /** Shared health after a non-lethal hit of {@code damage}; only environment damage counts. */
    public static double afterDamage(double shared, double damage, boolean environment) {
        return environment ? Math.max(0, shared - damage) : shared;
    }

    /** Health for the next member: the stored shared health (full if none), at least 1, at most max. */
    public static double forNext(Double stored, double max) {
        double h = stored == null ? max : stored;
        return Math.max(1.0, Math.min(max, h));
    }

    static double max(LivingEntity le) {
        var a = le.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    static Double stored(LivingEntity le) {
        return le.getPersistentDataContainer().get(Keys.STACK_HEALTH, PersistentDataType.DOUBLE);
    }

    /** Health the next member of le's stack should come in with. */
    public static double next(LivingEntity le) {
        return forNext(stored(le), max(le));
    }

    static void lower(LivingEntity le, double damage) {
        double now = afterDamage(stored(le) == null ? max(le) : stored(le), damage, true);
        le.getPersistentDataContainer().set(Keys.STACK_HEALTH, PersistentDataType.DOUBLE, now);
    }
}
