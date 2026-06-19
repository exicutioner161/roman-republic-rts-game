package units;

public final class ExperienceTable {
    private ExperienceTable() {}

    // Experience multiplier per level (index = level)
    private static final double[] MULTIPLIERS = {1.00, // 0
            1.08, // 1
            1.16, // 2
            1.24, // 3
            1.32, // 4
            1.40, // 5
            1.48, // 6
            1.56, // 7
            1.64, // 8
            1.72, // 9
            1.80 // 10
    };

    public static double getExperienceMultiplier(int level) {
        if (level < 0) {
            level = 0;
        }
        if (level >= MULTIPLIERS.length) {
            level = MULTIPLIERS.length - 1;
        }
        return MULTIPLIERS[level];
    }
}
