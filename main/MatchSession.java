package main;

import units.Unit;

public record MatchSession(String battleName, MatchMode matchMode, Unit.SoldierCulture playerCulture,
        int startingAllies, int startingEnemies) {
    public MatchSession {
        battleName = battleName != null ? battleName : "Battle";
        matchMode = matchMode != null ? matchMode : MatchMode.CUSTOM;
        playerCulture = playerCulture != null ? playerCulture : Unit.SoldierCulture.ROMAN;
        startingAllies = Math.max(1, startingAllies);
        startingEnemies = Math.max(0, startingEnemies);
    }

    public boolean rewardsCoins() { return matchMode == MatchMode.CAMPAIGN || matchMode == MatchMode.SANDBOX; }

    public int calculateWinCoins() {
        return rewardsCoins() ? MatchEconomy.calculateRewardCoins(startingEnemies, startingAllies) : 0;
    }
}