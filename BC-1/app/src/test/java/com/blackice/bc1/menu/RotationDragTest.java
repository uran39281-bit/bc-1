package com.blackice.bc1.menu;

import static org.junit.Assert.*;
import org.junit.Test;

public class RotationDragTest {
    @Test public void aFullClockwiseTurnAddsThreeSixty() {
        RotationDrag drag = new RotationDrag();
        drag.begin(0, 0);
        double value = 0;
        for (int i = 1; i <= 8; i++) value = drag.move(i * 45);
        assertEquals(360, value, 1e-9);
        for (int i = 9; i <= 16; i++) value = drag.move(i * 45);
        assertEquals("a second turn keeps counting; nothing wraps", 720, value, 1e-9);
    }

    @Test public void crossingTheSeamBothWaysIsContinuous() {
        RotationDrag drag = new RotationDrag();
        drag.begin(350, 700);
        assertEquals(710, drag.move(0), 1e-9);
        assertEquals(695, drag.move(345), 1e-9);
    }

    @Test public void negativeStartsAndCounterclockwiseTurnsWork() {
        RotationDrag drag = new RotationDrag();
        drag.begin(10, -30);
        assertEquals(-60, drag.move(340), 1e-9);
        double value = -60;
        for (int i = 1; i <= 8; i++) value = drag.move(340 - i * 45);
        assertEquals(-420, value, 1e-9);
    }

    @Test public void pressingDoesNotSnapToThePointer() {
        RotationDrag drag = new RotationDrag();
        drag.begin(123, 405);
        assertEquals(405, drag.rotationDegrees(), 0);
        assertEquals(405, drag.move(123), 0);
    }
}
