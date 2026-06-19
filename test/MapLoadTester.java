package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import world.TileMap;

public class MapLoadTester {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(System.getProperty("user.dir"), "resources", "maps", "caesar");
        if (!Files.isDirectory(dir)) {
            System.out.println("No caesar maps directory: " + dir);
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(p -> p.toString().toLowerCase().endsWith(".csv")).toList();
            for (Path p : files) {
                String rel = "/resources/maps/caesar/" + p.getFileName().toString();
                try {
                    System.out.println("Loading: " + rel);
                    TileMap m = new TileMap(rel);
                    System.out.println("Loaded: " + rel + " (" + m.getWidth() + "x" + m.getHeight() + ")");
                } catch (IOException _) {
                    System.out.println("Failed to load: " + rel);
                }
            }
        }
    }
}
