package th.park.datastack.mob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DeathPolicyTest {
    @Test void environmentKillsWholeStack() {
        for (String c : new String[]{"LAVA", "FIRE", "FIRE_TICK", "DROWNING", "FALL", "SUFFOCATION", "VOID",
                "HOT_FLOOR", "FREEZE", "STARVATION", "CONTACT", "LIGHTNING", "MELTING"})
            assertEquals(DeathPolicy.Outcome.WHOLE_STACK, DeathPolicy.of(c), c);
    }
    @Test void everythingElseKillsOne() {
        for (String c : new String[]{"ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE", "ENTITY_EXPLOSION",
                "BLOCK_EXPLOSION", "CRAMMING", "THORNS", "MAGIC", "POISON", "WITHER", "SONIC_BOOM"})
            assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of(c), c);
    }
    @Test void unknownKillsOne() {
        assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of("SOMETHING_NEW"));
        assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of(null));
    }
    @Test void entityLitFireKillsOne() {
        assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of("FIRE_TICK", true));
        assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of("FIRE", true));
        assertEquals(DeathPolicy.Outcome.WHOLE_STACK, DeathPolicy.of("FIRE_TICK", false));
        assertEquals(DeathPolicy.Outcome.WHOLE_STACK, DeathPolicy.of("FIRE", false));
    }
    @Test void flagDoesNotChangeOtherEnvironmentCauses() {
        for (String c : new String[]{"LAVA", "DROWNING", "FALL", "LIGHTNING", "HOT_FLOOR"})
            assertEquals(DeathPolicy.Outcome.WHOLE_STACK, DeathPolicy.of(c, true), c);
        assertEquals(DeathPolicy.Outcome.ONE, DeathPolicy.of("ENTITY_ATTACK", true));
    }
}
