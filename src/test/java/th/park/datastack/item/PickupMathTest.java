package th.park.datastack.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PickupMathTest {
    @Test void briefCases() {
        assertEquals(192, PickupMath.take(500, 64, 3, 0));
        assertEquals(100, PickupMath.take(100, 64, 3, 0));
        assertEquals(10, PickupMath.take(500, 64, 0, 10));
        assertEquals(3, PickupMath.take(5, 1, 3, 0));
        assertEquals(0, PickupMath.take(500, 64, 0, 0));
    }

    @Test void hopperTransferCappedAtOneStack() {
        assertEquals(64, PickupMath.hopperMove(500, 64, 5, 0));
        assertEquals(10, PickupMath.hopperMove(500, 64, 0, 10));
        assertEquals(30, PickupMath.hopperMove(30, 64, 5, 0));
    }
}
