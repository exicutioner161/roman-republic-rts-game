package main;

public final class MatchEconomy {
    private MatchEconomy() {}

    public static int calculateRewardCoins(int enemyCount, int allyCount) {
        int safeEnemyCount = Math.max(0, enemyCount);
        int safeAllyCount = Math.max(1, allyCount);
        return Math.max(0, (int)(1.5 * (((double)safeEnemyCount / safeAllyCount) + 1.0)));
    }
}