package org.geysermc.geyser.entity.type.display;

import org.cloudburstmc.math.imaginary.Quaternionf;
import org.cloudburstmc.math.vector.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayRotationTest {
    @Test
    void reflectsTheCoordinateFrameAndNormalizesWithoutChangingTheRotation() {
        var result = DisplayRotation.toBedrockQuaternion(Quaternionf.from(2, 3, 4, 5));
        double length = Math.sqrt(54);
        assertEquals(2 / length, result.getX(), 1e-6);
        assertEquals(-3 / length, result.getY(), 1e-6);
        assertEquals(-4 / length, result.getZ(), 1e-6);
        assertEquals(5 / length, result.getW(), 1e-6);
        assertEquals(1, result.lengthSquared(), 1e-6);
    }

    @Test
    void zeroAndNonFiniteQuaternionsHaveAStableIdentityFallback() {
        var identity = Vector4f.from(0, 0, 0, 1);
        assertEquals(identity, DisplayRotation.toBedrockQuaternion(Quaternionf.from(0, 0, 0, 0)));
        assertEquals(identity, DisplayRotation.toBedrockQuaternion(Quaternionf.from(Float.NaN, 0, 0, 1)));
        assertEquals(identity, DisplayRotation.toBedrockQuaternion(Quaternionf.from(0, Float.POSITIVE_INFINITY, 0, 1)));
    }

    @Test
    void antipodalInputsKeepEquivalentRotations() {
        var positive = DisplayRotation.toBedrockQuaternion(Quaternionf.from(1, -2, 3, -4));
        var negative = DisplayRotation.toBedrockQuaternion(Quaternionf.from(-1, 2, -3, 4));
        assertEquals(positive.negate(), negative);
    }
}
