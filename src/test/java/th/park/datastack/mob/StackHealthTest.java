package th.park.datastack.mob;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StackHealthTest {
    @Test void environmentLowersSharedHealth() {
        assertEquals(1.0, StackHealth.afterDamage(20, 19, true), 1e-9);
    }
    @Test void attacksOnlyHurtTheFrontMob() {
        assertEquals(20.0, StackHealth.afterDamage(20, 19, false), 1e-9);
    }
    @Test void environmentDamageAddsUp() {
        double h = StackHealth.afterDamage(20, 5, true);
        assertEquals(12.0, StackHealth.afterDamage(h, 3, true), 1e-9);
    }
    @Test void neverBelowZero() {
        assertEquals(0.0, StackHealth.afterDamage(4, 10, true), 1e-9);
    }
    @Test void untouchedStackComesInAtFullHealth() {
        assertEquals(20.0, StackHealth.forNext(null, 20), 1e-9);
    }
    @Test void nextMemberGetsSharedHealth() {
        assertEquals(1.0, StackHealth.forNext(1.0, 20), 1e-9);
    }
    @Test void nextMemberIsNeverDeadOnArrival() {
        assertEquals(1.0, StackHealth.forNext(0.0, 20), 1e-9);
    }
    @Test void clampedToMaxHealth() {
        assertEquals(10.0, StackHealth.forNext(20.0, 10), 1e-9);
    }
}
