package test;

import campaign.CampaignManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import main.GamePanel;
import units.Unit;
import world.TileMap;

public final class TestResourceValidation {
    private TestResourceValidation() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestResourceValidation.");
        }
        validateAllMaps();
        validateAllScenarios();
    }

    private static void validateAllMaps() throws Exception {
        Path mapsDir = Path.of(System.getProperty("user.dir"), "resources", "maps");
        require(Files.isDirectory(mapsDir), "Expected maps directory at " + mapsDir.toAbsolutePath());
        List<Path> mapFiles = listCsvFiles(mapsDir);
        require(!mapFiles.isEmpty(), "No map CSV files found under " + mapsDir.toAbsolutePath());
        for (Path mapPath : mapFiles) {
            TileMap map = TileMap.fromFile(mapPath.toFile());
            require(map != null, "Failed to load map: " + mapPath);
            require(map.getWidth() > 0 && map.getHeight() > 0, "Invalid dimensions for map: " + mapPath);
        }
        System.out.println("Validated " + mapFiles.size() + " map file(s).");
    }

    private static void validateAllScenarios() throws Exception {
        Path scenariosDir = Path.of(System.getProperty("user.dir"), "resources", "scenarios");
        require(Files.isDirectory(scenariosDir), "Expected scenarios directory at " + scenariosDir.toAbsolutePath());
        List<Path> scenarioFiles = listCsvFiles(scenariosDir);
        require(!scenarioFiles.isEmpty(), "No scenario CSV files found under " + scenariosDir.toAbsolutePath());
        CampaignManager campaignManager = new CampaignManager();
        for (Path scenarioPath : scenarioFiles) {
            CampaignManager.ScenarioDefinition scenarioDefinition = campaignManager
                    .loadScenarioDefinition(scenarioPath.toFile());
            require(scenarioDefinition != null, "Failed to load scenario: " + scenarioPath);
            TileMap map = loadScenarioMap(scenarioDefinition, scenarioPath.toFile());
            require(map != null,
                    "Scenario map failed to load for " + scenarioPath + ": " + scenarioDefinition.getMapPath());
            validateScenarioForces(scenarioDefinition, map, scenarioPath);
        }
        System.out.println("Validated " + scenarioFiles.size() + " scenario file(s).");
    }

    private static TileMap loadScenarioMap(CampaignManager.ScenarioDefinition scenarioDefinition, File scenarioFile)
            throws Exception {
        if (scenarioDefinition == null) {
            return null;
        }
        String mapPath = scenarioDefinition.getMapPath();
        if (mapPath == null || mapPath.isBlank()) {
            return null;
        }
        if (mapPath.startsWith("/")) {
            try {
                return new TileMap(mapPath);
            } catch (IOException _) {
                // fall back to disk resolution
            }
        }
        File resolvedMapFile = resolveScenarioMapFile(mapPath, scenarioFile);
        return resolvedMapFile != null ? TileMap.fromFile(resolvedMapFile) : null;
    }

    private static File resolveScenarioMapFile(String mapPath, File scenarioFile) {
        if (mapPath == null || mapPath.isBlank()) {
            return null;
        }
        File directFile = new File(mapPath);
        if (directFile.isAbsolute()) {
            return directFile;
        }
        File scenarioDirectory = scenarioFile != null ? scenarioFile.getParentFile() : null;
        return scenarioDirectory != null ? new File(scenarioDirectory, mapPath) : directFile;
    }

    private static void validateScenarioForces(CampaignManager.ScenarioDefinition scenarioDefinition, TileMap map,
            Path scenarioPath) {
        int mapWidthWorld = map.getWidth() * GamePanel.TILE_SIZE;
        int mapHeightWorld = map.getHeight() * GamePanel.TILE_SIZE;
        for (CampaignManager.ForcePlacement forcePlacement : scenarioDefinition.getForces()) {
            require(forcePlacement != null, "Scenario " + scenarioPath + " contains a null force placement entry.");
            int x = forcePlacement.getX();
            int y = forcePlacement.getY();
            require(x >= 0 && y >= 0 && x < mapWidthWorld && y < mapHeightWorld,
                    "Force placement out of map bounds in " + scenarioPath + " at (" + x + "," + y + ")");
            int col = Math.clamp(0, x / GamePanel.TILE_SIZE, map.getWidth() - 1);
            int row = Math.clamp(0, y / GamePanel.TILE_SIZE, map.getHeight() - 1);
            require(map.isPassable(col, row),
                    "Force placement on impassable tile in " + scenarioPath + " at tile (" + col + "," + row
                            + ") terrain=" + map.getTerrainType(col, row) + " structure="
                            + map.getStructureType(col, row));
            int size = forcePlacement.getSize();
            require(size > 0, "Force placement with non-positive size in " + scenarioPath + ": " + size);
            int experience = forcePlacement.getExperienceLevel();
            require(experience >= 0 && experience <= 10,
                    "Force placement with out-of-range experience in " + scenarioPath + ": " + experience);
            require(forcePlacement.getCulture() != null && forcePlacement.getType() != null,
                    "Force placement missing culture/type in " + scenarioPath);
        }
        for (Unit.SoldierCulture culture : Unit.SoldierCulture.values()) {
            int startingFood = scenarioDefinition.getStartingFood(culture);
            require(startingFood >= 0, "Starting food should never be negative in " + scenarioPath + " for " + culture);
        }
    }

    private static List<Path> listCsvFiles(Path dir) throws Exception {
        List<Path> csvFiles = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.filter(
                    path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted().forEach(csvFiles::add);
        }
        return csvFiles;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
