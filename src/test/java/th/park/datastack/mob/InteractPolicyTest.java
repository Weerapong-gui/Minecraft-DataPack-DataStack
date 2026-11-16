package th.park.datastack.mob;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InteractPolicyTest {
    @Test void nameTag() {
        assertTrue(InteractPolicy.nameTagSplits(10, true));
        assertFalse(InteractPolicy.nameTagSplits(1, true));
        assertFalse(InteractPolicy.nameTagSplits(10, false));
    }
    @Test void transform() {
        assertTrue(InteractPolicy.transformOne(5, "LIGHTNING"));
        assertTrue(InteractPolicy.transformOne(2, "CURED"));
        assertFalse(InteractPolicy.transformOne(1, "LIGHTNING"));
        assertFalse(InteractPolicy.transformOne(5, "SPLIT"));
    }
    @Test void growth() {
        assertTrue(InteractPolicy.grewUp(5, true, true));
        assertFalse(InteractPolicy.grewUp(5, true, false));
        assertFalse(InteractPolicy.grewUp(5, false, true));
        assertFalse(InteractPolicy.grewUp(1, true, true));
    }
}
