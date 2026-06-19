package persistence;

import java.nio.file.Path;

public final class UserProfileStore {
    private static final Path DEFAULT_PROFILE_PATH = Path.of(System.getProperty("user.dir"), "user_settings.txt");

    private UserProfileStore() {}

    public static Path getDefaultProfilePath() { return DEFAULT_PROFILE_PATH; }

    public static UserProfile load() { return load(DEFAULT_PROFILE_PATH); }

    public static UserProfile load(Path profilePath) {
        String serialized = PersistenceManager.loadStringFromFile(profilePath);
        if (serialized == null) {
            UserProfile defaults = UserProfile.defaults();
            save(profilePath, defaults);
            return defaults;
        }
        return UserProfile.fromFileText(serialized);
    }

    public static boolean save(UserProfile userProfile) { return save(DEFAULT_PROFILE_PATH, userProfile); }

    public static boolean save(Path profilePath, UserProfile userProfile) {
        if (profilePath == null || userProfile == null) {
            return false;
        }
        return PersistenceManager.saveStringToFile(profilePath, userProfile.toFileText());
    }

    public static UserProfile resetToDefaults() { return resetToDefaults(DEFAULT_PROFILE_PATH); }

    public static UserProfile resetToDefaults(Path profilePath) {
        UserProfile defaults = UserProfile.defaults();
        save(profilePath, defaults);
        return defaults;
    }
}