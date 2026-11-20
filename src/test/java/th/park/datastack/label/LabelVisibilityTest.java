package th.park.datastack.label;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LabelVisibilityTest {
    @Test void showsWithinRange() {
        assertTrue(LabelVisibility.visible(0, 10));
        assertTrue(LabelVisibility.visible(9.9 * 9.9, 10));
    }
    @Test void showsExactlyAtRange() {
        assertTrue(LabelVisibility.visible(100, 10));
    }
    @Test void hidesBeyondRange() {
        assertFalse(LabelVisibility.visible(10.1 * 10.1, 10));
        assertFalse(LabelVisibility.visible(64 * 64, 10));
    }
    @Test void zeroOrNegativeRangeMeansAlwaysShow() {
        assertTrue(LabelVisibility.visible(1_000_000, 0));
        assertTrue(LabelVisibility.visible(1_000_000, -1));
    }
}
