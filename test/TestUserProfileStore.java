package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import persistence.UserProfile;
import persistence.UserProfileStore;

public final class TestUserProfileStore {
    private TestUserProfileStore() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestUserProfileStore.");
        }
        verifyRoundTripPersistsProfileValues();
        verifyResetRestoresDefaultSettings();
        verifyParserIgnoresUnknownAndInvalidLines();
    }

    private static void verifyRoundTripPersistsProfileValues() throws IOException {
        Path tempFile = Files.createTempFile("romanrts-profile-roundtrip", ".txt");
        try {
            UserProfile expected = new UserProfile(27, false, false);
            require(UserProfileStore.save(tempFile, expected), "Expected saving the user profile to succeed.");
            UserProfile actual = UserProfileStore.load(tempFile);
            require(expected.equals(actual), "Expected the saved user profile to round-trip without changes.");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void verifyResetRestoresDefaultSettings() throws IOException {
        Path tempFile = Files.createTempFile("romanrts-profile-reset", ".txt");
        try {
            require(UserProfileStore.save(tempFile, new UserProfile(99, false, false)),
                    "Expected seeding the profile to succeed before reset.");
            UserProfile resetProfile = UserProfileStore.resetToDefaults(tempFile);
            require(UserProfile.defaults().equals(resetProfile),
                    "Expected resetToDefaults to return the default profile values.");
            require(UserProfile.defaults().equals(UserProfileStore.load(tempFile)),
                    "Expected resetToDefaults to overwrite the profile file with defaults.");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void verifyParserIgnoresUnknownAndInvalidLines() {
        String profileText = """
                coins=18
                unknown_key=value
                launch_fullscreen=false
                coins=not_a_number
                show_scenario_briefings=false
                """;
        UserProfile parsed = UserProfile.fromFileText(profileText);
        require(parsed.coins() == 18, "Expected a later invalid coin line to preserve the last valid coin value.");
        require(!parsed.launchFullscreen(), "Expected launch_fullscreen to parse from the profile text.");
        require(!parsed.showScenarioBriefings(), "Expected show_scenario_briefings to parse from the profile text.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}