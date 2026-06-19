package test;

import main.MatchEconomy;

public final class TestMatchEconomy {
    private TestMatchEconomy() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestMatchEconomy.");
        }
        verifyRewardFormulaUsesRelativeForceRatio();
        verifyRewardFormulaHandlesEmptyOrTinyAllyCounts();
    }

    private static void verifyRewardFormulaUsesRelativeForceRatio() {
        require(MatchEconomy.calculateRewardCoins(3, 4) == 2,
                "Expected a smaller enemy force to award two coins against four allied formations.");
        require(MatchEconomy.calculateRewardCoins(12, 4) == 6,
                "Expected a 3:1 enemy-to-ally ratio to award six coins.");
    }

    private static void verifyRewardFormulaHandlesEmptyOrTinyAllyCounts() {
        require(MatchEconomy.calculateRewardCoins(0, 0) == 1,
                "Expected the reward formula to treat zero allied forces as a minimum divisor of one.");
        require(MatchEconomy.calculateRewardCoins(5, 1) == 9,
                "Expected a lone-force win against five enemies to award nine coins.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}