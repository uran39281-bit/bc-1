package com.blackice.bc1.menu;

/**
 * Accumulates dial-pointer changes without normalizing the document angle.
 * Pointer degrees must use one consistent direction/origin (for example,
 * clockwise from 12 o'clock). Like any sampled dial, it assumes successive
 * pointer samples move less than 180 degrees; a half-turn is ambiguous.
 * One begin/move/end sequence is one document transaction in the caller.
 */
public final class RotationDrag {
    private boolean active;
    private double lastPointer;
    private double rotation;

    public void begin(double pointerDegrees, double currentRotationDegrees) {
        double pointer = normalized(pointerDegrees);
        finite(currentRotationDegrees);
        lastPointer = pointer;
        rotation = currentRotationDegrees;
        active = true;
    }

    public double move(double pointerDegrees) {
        if (!active) throw new IllegalStateException("begin must be called before move");
        double pointer = normalized(pointerDegrees);
        double raw = pointer - lastPointer;
        double delta = raw;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        double next = rotation + delta;
        finite(next);
        lastPointer = pointer;
        rotation = next;
        return rotation;
    }

    /** End the gesture; persistence/undo is owned by the caller. */
    public double end() {
        active = false;
        return rotation;
    }

    public boolean isActive() { return active; }
    public double rotationDegrees() { return rotation; }

    private static double normalized(double n) {
        finite(n);
        double result = n % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }

    private static void finite(double n) {
        if (!Double.isFinite(n)) throw new IllegalArgumentException("angle must be finite");
    }
}
