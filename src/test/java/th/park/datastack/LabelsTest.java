package th.park.datastack;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LabelsTest {
    @Test void noLabelAtOrBelowThreshold() {
        assertNull(Labels.text(1, 1));
        assertNull(Labels.text(64, 64));
    }
    @Test void labelAboveThreshold() {
        assertEquals("x2", Labels.text(2, 1));
        assertEquals("x65", Labels.text(65, 64));
        assertEquals("x1000", Labels.text(1000, 1));
    }
}
