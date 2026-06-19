package persistence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

@SuppressWarnings("CallToPrintStackTrace")
public class PersistenceManager {
    private PersistenceManager() {}

    public static boolean saveStringToFile(String path, String content) {
        try (FileWriter fileWriter = new FileWriter(path)) {
            fileWriter.write(content);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String loadStringFromFile(File file) {
        try (Scanner input = new Scanner(file)) {
            input.useDelimiter("\\A");
            return input.hasNext() ? input.next() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean saveStringToFile(Path path, String content) {
        if (path == null) {
            return false;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content != null ? content : "", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String loadStringFromFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}