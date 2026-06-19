package main;

public record MatchResult(String battleName, MatchMode matchMode, boolean victory, int rewardCoins) {
    public MatchResult {
        battleName = battleName != null ? battleName : "Battle";
        matchMode = matchMode != null ? matchMode : MatchMode.CUSTOM;
        rewardCoins = Math.max(0, rewardCoins);
    }
}