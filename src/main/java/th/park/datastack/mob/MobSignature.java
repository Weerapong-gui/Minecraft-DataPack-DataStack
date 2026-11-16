package th.park.datastack.mob;

import org.bukkit.Keyed;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import th.park.datastack.DataStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import th.park.datastack.Keys;
import th.park.datastack.Labels;
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
        // a custom name that is not our xN label (or lacks our marker) is a player-given name
        if (e.customName() != null && (!e.getPersistentDataContainer().has(Keys.LABEL, PersistentDataType.BYTE)
            || !Labels.isOurs(PlainTextComponentSerializer.plainText().serialize(e.customName())))) return false;
        // any gear (incl. picked-up player items, captain banners) would vanish or dupe on absorb
        EntityEquipment eq = e.getEquipment();
        if (eq != null) for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!e.canUseEquipmentSlot(slot)) continue;
            ItemStack it = eq.getItem(slot);
            if (it != null && !it.isEmpty()) return false;
        }
        if (e.getPersistentDataContainer().has(MobDeathListener.DYING, PersistentDataType.BYTE)) return false;
        // trades and reputation would be lost on absorb
        if (e instanceof AbstractVillager av && av.getRecipeCount() > 0) return false;
        if (e instanceof Villager v && v.getVillagerExperience() > 0) return false;
        // raid / patrol members: absorbing them breaks raid wave tracking
        if (e instanceof Raider rd && (rd.getRaid() != null || rd.isPatrolLeader())) return false;
        if (e.isLeashed() || e.isInsideVehicle() || !e.getPassengers().isEmpty()) return false;
        return StackCount.get(e) < DataStack.get().cfg().maxMobStack();
    }

    /** Type + age + per-type variant; mobs merge only when these strings are equal. */
    public static String of(LivingEntity e) {
        StringBuilder sb = new StringBuilder(e.getType().name());
        sb.append(e.getRemoveWhenFarAway() ? "|despawnable" : "|persistent");
        if (e instanceof Ageable a) sb.append(a.isAdult() ? "|adult" : "|baby");
        if (e instanceof Sheep s) sb.append('|').append(s.getColor()).append(s.isSheared() ? "|sheared" : "|wool");
        if (e instanceof Villager v) sb.append('|').append(k(v.getProfession())).append('|')
            .append(k(v.getVillagerType())).append('|').append(v.getVillagerLevel());
        if (e instanceof ZombieVillager z) sb.append('|').append(k(z.getVillagerProfession()));
        if (e instanceof Cat c) sb.append('|').append(k(c.getCatType()));
        if (e instanceof Frog f) sb.append('|').append(k(f.getVariant()));
        if (e instanceof Axolotl x) sb.append('|').append(x.getVariant());
        if (e instanceof Parrot p) sb.append('|').append(p.getVariant());
        if (e instanceof Horse h) sb.append('|').append(h.getColor()).append('|').append(h.getStyle());
        if (e instanceof MushroomCow m) sb.append('|').append(m.getVariant());
        if (e instanceof Rabbit r) sb.append('|').append(r.getRabbitType());
        if (e instanceof Fox f) sb.append('|').append(f.getFoxType());
        if (e instanceof Llama l) sb.append('|').append(l.getColor());
        if (e instanceof Wolf w) sb.append('|').append(k(w.getVariant()));
        if (e instanceof Panda p) sb.append('|').append(p.getMainGene()).append('|').append(p.getHiddenGene());
        if (e instanceof Goat g) sb.append('|').append(g.hasLeftHorn()).append('|').append(g.hasRightHorn());
        if (e instanceof TropicalFish t) sb.append('|').append(t.getPattern()).append('|')
            .append(t.getBodyColor()).append('|').append(t.getPatternColor());
        if (e instanceof Slime s) sb.append('|').append(s.getSize());
        return sb.toString();
    }

    private static String k(Object o) {
        return o instanceof Keyed kd ? kd.getKey().toString() : String.valueOf(o);
    }
}
