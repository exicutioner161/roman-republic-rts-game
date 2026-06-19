package test;

import java.awt.Point;
import utils.MouseHandler;

public class TestFormationControls {
    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestFormationControls.");
        }
        verifyFormationFacingMappings();
    }

    private static void verifyFormationFacingMappings() {
        requireAngle(MouseHandler.resolveFormationFacingAngle(new Point(0, 0), new Point(20, 0)), -Math.PI * 0.5,
                "Expected left-to-right formation drag to face north.");
        requireAngle(MouseHandler.resolveFormationFacingAngle(new Point(0, 0), new Point(0, 20)), 0.0,
                "Expected top-to-bottom formation drag to face east.");
        requireAngle(MouseHandler.resolveFormationFacingAngle(new Point(20, 0), new Point(0, 0)), Math.PI * 0.5,
                "Expected right-to-left formation drag to face south.");
        requireAngle(MouseHandler.resolveFormationFacingAngle(new Point(0, 20), new Point(0, 0)), Math.PI,
                "Expected bottom-to-top formation drag to face west.");
    }

    private static void requireAngle(Double actualAngle, double expectedAngle, String message) {
        if (actualAngle == null || Math.abs(actualAngle - expectedAngle) > 1e-9) {
            throw new IllegalStateException(
                    message + " Actual angle = " + actualAngle + " expected = " + expectedAngle);
        }
    }
}