package test;

import units.ExperienceTable;

public final class TestExperienceTable {
    private TestExperienceTable() {}

    public static void main(String[] args) {
        verifyMonotonicAndBounds();
        System.out.println("TestExperienceTable: OK");
    }

    private static void verifyMonotonicAndBounds() {
        double prev = ExperienceTable.getExperienceMultiplier(0);
        require(Math.abs(prev - 1.0) < 1e-9, "Level 0 should be 1.0");
        for (int i = 1; i <= 10; i++) {
            double cur = ExperienceTable.getExperienceMultiplier(i);
            require(cur >= prev, "Experience multiplier should not decrease at level " + i);
            prev = cur;
        }
        require(ExperienceTable.getExperienceMultiplier(-5) == ExperienceTable.getExperienceMultiplier(0),
                "Negative level should clamp to 0");
        require(ExperienceTable.getExperienceMultiplier(999) == ExperienceTable.getExperienceMultiplier(10),
                "High level should clamp to max defined level");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
