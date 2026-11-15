package th.park.datastack.mob;

import org.bukkit.Keyed;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import th.park.datastack.DataStack;
import th.park.datastack.Keys;
import th.park.datastack.StackCount;

/** Decides which mobs may stack and which ones count as "the same kind". */
public final class MobSignature {
    private MobSignature() {}

    /** True if this mob may absorb or be absorbed (spec rules 1, 2 and the never-merge list). */
    public static boolean eligible(LivingEntity e) {
        if (!(e instanceof Mob) || !e.isValid() || e.isDead()) return false;
        if (e instanceof EnderDragon || e instanceof Wither || e instanceof Warden) return false;
        if (e instanceof Tameable t && t.isTamed()) return false;
        // a custom name without our label marker is a player-given name
        if (e.customName() != null && !e.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE)) return false;
        if (e.getPersistentDataContainer().has(MobDeathListener.DYING, PersistentDataType.BYTE)) return false;
        if (e.isLeashed() || e.isInsideVehicle() || !e.getPassengers().isEmpty()) return false;
        return StackCount.get(e) < DataStack.get().cfg().maxMobStack();
    }

    /** Type + age + per-type variant; mobs merge only when these strings are equal. */
    public static String of(LivingEntity e) {
        StringBuilder sb = new StringBuilder(e.getType().name());
        if (e instanceof Ageable a) sb.append(a.isAdult() ? "|adult" : "|baby");
        if (e instanceof Sheep s) sb.append('|').append(s.getColor());
        if (e instanceof Villager v) sb.append('|').append(k(v.getProfession())).append('|')
            .append(k(v.getVillagerType())).append('|').append(v.getVillagerLevel());
        if (e instanceof ZombieVillager z) sb.append('|').append(k(z.getVillagerProfession()));
        if (e instanceof Cat c) sb.append('|').append(k(c.getCatType()));
        if (e instanceof Frog f) sb.append('|').append(k(f.getVariant()));
        if (e instanceof Axolotl x) sb.append('|').append(x.getVariant());
        if (e instanceof Parrot p) sb.append('|').append(p.getVariant());
        if (e instanceof Horse h) sb.append('|').append(h.getColor()).append('|').append(h.getStyle());
        if (e instanceof Slime s) sb.append('|').append(s.getSize());
        return sb.toString();
    }

    private static String k(Object o) {
        return o instanceof Keyed kd ? kd.getKey().toString() : String.valueOf(o);
    }
}
