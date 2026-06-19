package persistence;

public record UserProfile(int coins, boolean launchFullscreen, boolean showScenarioBriefings) {
    public static final String COINS_KEY = "coins";
    public static final String LAUNCH_FULLSCREEN_KEY = "launch_fullscreen";
    public static final String SHOW_BRIEFINGS_KEY = "show_scenario_briefings";
    public static final int DEFAULT_COINS = 0;
    public static final boolean DEFAULT_LAUNCH_FULLSCREEN = true;
    public static final boolean DEFAULT_SHOW_SCENARIO_BRIEFINGS = true;

    public UserProfile { coins = Math.max(0, coins); }

    public static UserProfile defaults() {
        return new UserProfile(DEFAULT_COINS, DEFAULT_LAUNCH_FULLSCREEN, DEFAULT_SHOW_SCENARIO_BRIEFINGS);
    }

    public UserProfile withCoins(int updatedCoins) {
        return new UserProfile(updatedCoins, launchFullscreen, showScenarioBriefings);
    }

    public UserProfile withLaunchFullscreen(boolean updatedLaunchFullscreen) {
        return new UserProfile(coins, updatedLaunchFullscreen, showScenarioBriefings);
    }

    public UserProfile withShowScenarioBriefings(boolean updatedShowScenarioBriefings) {
        return new UserProfile(coins, launchFullscreen, updatedShowScenarioBriefings);
    }

    public String toFileText() {
        return new StringBuilder().append(COINS_KEY).append('=').append(coins).append('\n')
                .append(LAUNCH_FULLSCREEN_KEY).append('=').append(launchFullscreen).append('\n')
                .append(SHOW_BRIEFINGS_KEY).append('=').append(showScenarioBriefings).append('\n').toString();
    }

    public static UserProfile fromFileText(String text) {
        UserProfile defaults = defaults();
        if (text == null || text.isBlank()) {
            return defaults;
        }
        int resolvedCoins = defaults.coins();
        boolean resolvedLaunchFullscreen = defaults.launchFullscreen();
        boolean resolvedShowBriefings = defaults.showScenarioBriefings();
        for (String line : text.split("\\R")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, separatorIndex).trim().toLowerCase();
            String value = trimmed.substring(separatorIndex + 1).trim();
            switch (key) {
            case COINS_KEY -> resolvedCoins = parseNonNegativeInt(value, resolvedCoins);
            case LAUNCH_FULLSCREEN_KEY -> resolvedLaunchFullscreen = Boolean.parseBoolean(value);
            case SHOW_BRIEFINGS_KEY -> resolvedShowBriefings = Boolean.parseBoolean(value);
            default -> {
                // Ignore unknown settings for forward compatibility.
            }
            }
        }
        return new UserProfile(resolvedCoins, resolvedLaunchFullscreen, resolvedShowBriefings);
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }
}