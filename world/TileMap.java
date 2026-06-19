package world;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import main.GamePanel;
import persistence.PersistenceManager;
import units.Unit;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;

public class TileMap {
    private static final String DEFAULT_BATTLE_NAME = "Untitled Battle";
    private static final double FORT_ZONE_RADIUS_TILES = 3.25;
    private final int width;
    private final int height;
    private final int[] tiles;
    private final int[] structures;
    private final double[] moveCost;
    private String battleName;
    public static final int DEFAULT_TILE_INDEX = TerrainType.FLAT_GRASS.ordinal();
    private static final Logger LOGGER = Logger.getLogger(TileMap.class.getName());

    // CSV format:
    // line 1: battle name
    // line 2: width,height
    // following lines: rows of comma-separated tile indices
    public TileMap(String resourcePath) throws IOException { this(loadCsvSource(resourcePath)); }

    private TileMap(CsvSource csvSource) {
        this(csvSource.layout());
        loadFromCsvString(csvSource.csvText());
    }

    private TileMap(CsvLayout layout) { this(layout.battleName(), layout.width(), layout.height()); }

    public TileMap(int width, int height) { this(width, height, DEFAULT_TILE_INDEX); }

    public TileMap(String battleName, int width, int height) { this(battleName, width, height, DEFAULT_TILE_INDEX); }

    public TileMap(int width, int height, int defaultTileIndex) {
        this(DEFAULT_BATTLE_NAME, width, height, defaultTileIndex);
    }

    public TileMap(String battleName, int width, int height, int defaultTileIndex) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Map width and height must be positive.");
        }
        this.battleName = normalizeBattleName(battleName);
        this.width = width;
        this.height = height;
        this.tiles = new int[width * height];
        this.structures = new int[width * height];
        this.moveCost = new double[width * height];
        Arrays.fill(this.tiles, defaultTileIndex);
        Arrays.fill(this.structures, StructureType.NONE.ordinal());
        Arrays.fill(this.moveCost, getDefaultMoveCost(defaultTileIndex));
    }

    public static TileMap fromCsvString(String csvText) {
        if (csvText == null || csvText.isBlank()) {
            throw new IllegalArgumentException("CSV text cannot be blank.");
        }
        return new TileMap(new CsvSource(csvText, parseCsvLayout(csvText)));
    }

    public static TileMap fromFile(File file) {
        String csvText = PersistenceManager.loadStringFromFile(file);
        if (csvText == null || csvText.isBlank()) {
            return null;
        }
        return fromCsvString(csvText);
    }

    private static CsvLayout parseCsvLayout(String csvText) {
        try (Scanner s = new Scanner(csvText)) {
            String first = s.hasNextLine() ? s.nextLine().trim() : "";
            String dimensionLine = first;
            String battleName = DEFAULT_BATTLE_NAME;
            if (!isDimensionLine(first) && s.hasNextLine()) {
                battleName = normalizeBattleName(first);
                dimensionLine = s.nextLine().trim();
            }
            String[] wh = dimensionLine.split(",");
            int width = Integer.parseInt(wh[0].trim());
            int height = Integer.parseInt(wh[1].trim());
            return new CsvLayout(battleName, width, height);
        }
    }

    private void loadFromCsvString(String csvText) {
        try (Scanner s = new Scanner(csvText)) {
            if (!s.hasNextLine()) {
                return;
            }
            initializeBattleNameAndAdvance(s, s.nextLine().trim());
            loadTerrainRows(s);
            loadOptionalSections(s);
        }
    }

    private void initializeBattleNameAndAdvance(Scanner scanner, String firstLine) {
        if (isDimensionLine(firstLine)) {
            battleName = DEFAULT_BATTLE_NAME;
            return;
        }
        battleName = normalizeBattleName(firstLine);
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }
    }

    private void loadTerrainRows(Scanner scanner) {
        int row = 0;
        while (scanner.hasNextLine() && row < height) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            loadTerrainRow(row, line);
            row++;
        }
    }

    private void loadTerrainRow(int row, String line) {
        String[] parts = line.split(",");
        int limit = Math.min(parts.length, width);
        for (int col = 0; col < limit; col++) {
            String token = parts[col].trim();
            int index = parseTerrainToken(token, row, col);
            setTileIndex(col, row, index);
        }
    }

    private static int parseTokenAsIndex(String token, int defaultValue, String tokenLabel, int row, int col) {
        if (token == null || token.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException _) {
            String cleaned = stripNonNumericChars(token);
            if (cleaned.isBlank()) {
                LOGGER.log(Level.FINE, "Invalid {0} token at row {1}, col {2}: {3}",
                        new Object[] {tokenLabel, row, col, token});
                return defaultValue;
            }
            try {
                return Integer.parseInt(cleaned);
            } catch (NumberFormatException _) {
                LOGGER.log(Level.FINE, "Invalid {0} token at row {1}, col {2}: {3}",
                        new Object[] {tokenLabel, row, col, token});
                return defaultValue;
            }
        }
    }

    private static String stripNonNumericChars(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(token.length());
        for (int index = 0; index < token.length(); index++) {
            char c = token.charAt(index);
            if ((c >= '0' && c <= '9') || c == '-') {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    private int parseTerrainToken(String token, int row, int col) {
        return parseTokenAsIndex(token, DEFAULT_TILE_INDEX, "terrain", row, col);
    }

    private void loadOptionalSections(Scanner scanner) {
        while (scanner.hasNextLine()) {
            String sectionHeader = scanner.nextLine().trim();
            if (sectionHeader.isEmpty()) {
                continue;
            }
            if ("structures".equalsIgnoreCase(sectionHeader)) {
                loadStructureRows(scanner);
                return;
            }
        }
    }

    private void loadStructureRows(Scanner scanner) {
        int row = 0;
        while (scanner.hasNextLine() && row < height) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            int limit = Math.min(parts.length, width);
            for (int col = 0; col < limit; col++) {
                String token = parts[col].trim();
                int structureIndex = parseStructureToken(token, row, col);
                setStructureIndex(col, row, structureIndex);
            }
            row++;
        }
    }

    private int parseStructureToken(String token, int row, int col) {
        return parseTokenAsIndex(token, StructureType.NONE.ordinal(), "structure", row, col);
    }

    public static TileMap createBlank(int width, int height) { return new TileMap(width, height, DEFAULT_TILE_INDEX); }

    public static TileMap createBlank(int width, int height, int defaultTileIndex) {
        return new TileMap(width, height, defaultTileIndex);
    }

    public static TileMap createBlank(String battleName, int width, int height, int defaultTileIndex) {
        return new TileMap(battleName, width, height, defaultTileIndex);
    }

    private static String loadResourceAsString(String path) throws IOException {
        try (InputStream in = TileMap.class.getResourceAsStream(path)) {
            if (in != null) {
                try (Scanner s = new Scanner(in, StandardCharsets.UTF_8.name())) {
                    s.useDelimiter("\\A");
                    return s.hasNext() ? s.next() : "";
                }
            }
            Path fallbackPath = resolveResourceFallbackPath(path);
            if (fallbackPath != null) {
                return Files.readString(fallbackPath, StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("Resource not found: " + path);
        }
    }

    private static Path resolveResourceFallbackPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        Path candidate = Path.of(System.getProperty("user.dir"), normalizedPath);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static String normalizeResourcePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Map resource path cannot be blank.");
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static CsvSource loadCsvSource(String resourcePath) throws IOException {
        String csvText = loadResourceAsString(normalizeResourcePath(resourcePath));
        return new CsvSource(csvText, parseCsvLayout(csvText));
    }

    private int toIndex(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            throw new IndexOutOfBoundsException("Tile coordinates out of range: (" + col + ", " + row + ")");
        }
        return row * width + col;
    }

    private int normalizeTileIndex(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= TerrainType.values().length) {
            return DEFAULT_TILE_INDEX;
        }
        return tileIndex;
    }

    private double getDefaultMoveCost(int tileIndex) {
        return TerrainType.values()[normalizeTileIndex(tileIndex)].getBaseMoveCost();
    }

    private int normalizeStructureIndex(int structureIndex) {
        if (structureIndex < 0 || structureIndex >= StructureType.values().length) {
            return StructureType.NONE.ordinal();
        }
        return structureIndex;
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public String getBattleName() { return battleName; }

    public void setBattleName(String battleName) { this.battleName = normalizeBattleName(battleName); }

    public int getTileIndex(int col, int row) {
        return col < 0 || row < 0 || col >= width || row >= height ? -1 : tiles[row * width + col];
    }

    public void setTileIndex(int col, int row, int tileIndex) {
        int index = toIndex(col, row);
        int normalizedTileIndex = normalizeTileIndex(tileIndex);
        tiles[index] = normalizedTileIndex;
        moveCost[index] = getDefaultMoveCost(normalizedTileIndex);
    }

    public TerrainType getTerrainType(int col, int row) {
        int index = getTileIndex(col, row);
        if (index < 0) {
            return TerrainType.FLAT_GRASS;
        }
        return TerrainType.values()[normalizeTileIndex(index)];
    }

    public StructureType getStructureType(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return StructureType.NONE;
        }
        return StructureType.values()[normalizeStructureIndex(structures[row * width + col])];
    }

    public StructureType getStructureTypeAtWorld(double worldX, double worldY) {
        int col = Math.clamp((int)Math.floor(worldX / GamePanel.TILE_SIZE), 0, width - 1);
        int row = Math.clamp((int)Math.floor(worldY / GamePanel.TILE_SIZE), 0, height - 1);
        return getStructureType(col, row);
    }

    public boolean isBridgeDeckHorizontal(int col, int row) {
        if (getStructureType(col, row) != StructureType.BRIDGE) {
            return true;
        }
        int horizontalBridgeLinks = countAdjacentBridges(col, row, true);
        int verticalBridgeLinks = countAdjacentBridges(col, row, false);
        if (horizontalBridgeLinks != verticalBridgeLinks) {
            return horizontalBridgeLinks > verticalBridgeLinks;
        }
        int verticalRiverFlow = countAdjacentRiverContinuity(col, row, false);
        int horizontalRiverFlow = countAdjacentRiverContinuity(col, row, true);
        if (verticalRiverFlow != horizontalRiverFlow) {
            return verticalRiverFlow > horizontalRiverFlow;
        }
        return true;
    }

    public double getFortInfluenceAt(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return 0.0;
        }
        int searchRadius = (int)Math.ceil(FORT_ZONE_RADIUS_TILES);
        double bestInfluence = 0.0;
        for (int scanRow = Math.max(0, row - searchRadius); scanRow <= Math.min(height - 1,
                row + searchRadius); scanRow++) {
            for (int scanCol = Math.max(0, col - searchRadius); scanCol <= Math.min(width - 1,
                    col + searchRadius); scanCol++) {
                if (!getStructureType(scanCol, scanRow).isFort()) {
                    continue;
                }
                double distance = Math.hypot((double)scanCol - col, (double)scanRow - row);
                double influence = Math.max(0.0, 1.0 - distance / FORT_ZONE_RADIUS_TILES);
                bestInfluence = Math.max(bestInfluence, influence);
            }
        }
        return bestInfluence;
    }

    public double getFortInfluenceAtWorld(double worldX, double worldY) {
        int col = Math.clamp((int)Math.floor(worldX / GamePanel.TILE_SIZE), 0, width - 1);
        int row = Math.clamp((int)Math.floor(worldY / GamePanel.TILE_SIZE), 0, height - 1);
        return getFortInfluenceAt(col, row);
    }

    public double getFortMissileCoverMultiplierAtWorld(double worldX, double worldY) {
        double influence = getFortInfluenceAtWorld(worldX, worldY);
        return Math.clamp(1.0 - influence * 0.18, 0.82, 1.0);
    }

    public TerrainType getTerrainTypeAtWorld(double worldX, double worldY) {
        int col = Math.clamp((int)Math.floor(worldX / GamePanel.TILE_SIZE), 0, width - 1);
        int row = Math.clamp((int)Math.floor(worldY / GamePanel.TILE_SIZE), 0, height - 1);
        return getTerrainType(col, row);
    }

    public int getHeightLevel(int col, int row) { return getTerrainType(col, row).getHeightLevel(); }

    public int getHeightLevelAtWorld(double worldX, double worldY) {
        return getTerrainTypeAtWorld(worldX, worldY).getHeightLevel();
    }

    public double getTraversalCost(int col, int row, Unit.SoldierClass soldierClass) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return Double.POSITIVE_INFINITY;
        }
        TerrainType terrainType = getTerrainType(col, row);
        StructureType structureType = getStructureType(col, row);
        if (structureType.isBridge()) {
            return getBridgeTraversalCost(soldierClass);
        }
        if (!terrainType.isPassable() || !structureType.isPassable()) {
            return Double.POSITIVE_INFINITY;
        }
        double cost = terrainType.getBaseMoveCost();
        if (structureType != StructureType.NONE) {
            cost *= structureType.getTraversalMultiplier();
        }
        if (soldierClass == null) {
            return cost;
        }
        return switch (soldierClass) {
        case CAVALRY -> switch (terrainType) {
        case FLAT_GRASS -> cost * 0.82;
        case HILLY_GRASS -> cost * 1.20;
        case MOUNTAIN_PASS -> cost * 1.28;
        case RIVER -> cost * 1.42;
        case FLAT_FOREST -> cost * 1.35;
        case HILLY_FOREST -> cost * 1.50;
        case MOUNTAINOUS_FOREST -> cost * 1.65;
        case MARSHLAND -> cost * 1.72;
        case FORESTED_MARSHLAND -> cost * 1.92;
        case FLAT_MUD -> cost * 1.30;
        case FORESTED_MUD -> cost * 1.56;
        case HILLY_MUD -> cost * 1.46;
        case IMPASSABLE_MOUNTAIN -> Double.POSITIVE_INFINITY;
        };
        case HORSE_ARCHER -> switch (terrainType) {
        case FLAT_GRASS -> cost * 0.90;
        case HILLY_GRASS -> cost * 1.10;
        case MOUNTAIN_PASS -> cost * 1.18;
        case RIVER -> cost * 1.32;
        case FLAT_FOREST -> cost * 1.25;
        case HILLY_FOREST -> cost * 1.38;
        case MOUNTAINOUS_FOREST -> cost * 1.52;
        case MARSHLAND -> cost * 1.52;
        case FORESTED_MARSHLAND -> cost * 1.72;
        case FLAT_MUD -> cost * 1.20;
        case FORESTED_MUD -> cost * 1.42;
        case HILLY_MUD -> cost * 1.30;
        case IMPASSABLE_MOUNTAIN -> Double.POSITIVE_INFINITY;
        };
        case ARCHER -> switch (terrainType) {
        case FLAT_GRASS -> cost * 1.02;
        case HILLY_GRASS -> cost * 1.06;
        case MOUNTAIN_PASS -> cost * 1.12;
        case RIVER -> cost * 1.22;
        case FLAT_FOREST -> cost * 1.14;
        case HILLY_FOREST -> cost * 1.24;
        case MOUNTAINOUS_FOREST -> cost * 1.34;
        case MARSHLAND -> cost * 1.24;
        case FORESTED_MARSHLAND -> cost * 1.36;
        case FLAT_MUD -> cost * 1.12;
        case FORESTED_MUD -> cost * 1.24;
        case HILLY_MUD -> cost * 1.18;
        case IMPASSABLE_MOUNTAIN -> Double.POSITIVE_INFINITY;
        };
        case INFANTRY -> switch (terrainType) {
        case FLAT_GRASS -> cost;
        case HILLY_GRASS -> cost * 1.05;
        case MOUNTAIN_PASS -> cost * 1.12;
        case RIVER -> cost * 1.18;
        case FLAT_FOREST -> cost * 1.10;
        case HILLY_FOREST -> cost * 1.18;
        case MOUNTAINOUS_FOREST -> cost * 1.28;
        case MARSHLAND -> cost * 1.16;
        case FORESTED_MARSHLAND -> cost * 1.26;
        case FLAT_MUD -> cost * 1.08;
        case FORESTED_MUD -> cost * 1.18;
        case HILLY_MUD -> cost * 1.14;
        case IMPASSABLE_MOUNTAIN -> Double.POSITIVE_INFINITY;
        };
        };
    }

    private double getBridgeTraversalCost(Unit.SoldierClass soldierClass) {
        double bridgeCost = 1.05;
        if (soldierClass == null) {
            return bridgeCost;
        }
        return switch (soldierClass) {
        case CAVALRY, HORSE_ARCHER -> bridgeCost * 0.98;
        case ARCHER -> bridgeCost * 1.02;
        case INFANTRY -> bridgeCost;
        };
    }

    public void setTerrainType(int col, int row, TerrainType terrainType) {
        setTileIndex(col, row, terrainType == null ? DEFAULT_TILE_INDEX : terrainType.ordinal());
        StructureType structureType = getStructureType(col, row);
        if (!canPlaceStructure(col, row, structureType)) {
            setStructureType(col, row, StructureType.NONE);
        }
    }

    public void setStructureType(int col, int row, StructureType structureType) {
        StructureType normalizedType = structureType == null ? StructureType.NONE : structureType;
        if (normalizedType != StructureType.NONE && !canPlaceStructure(col, row, normalizedType)) {
            normalizedType = StructureType.NONE;
        }
        setStructureIndex(col, row, normalizedType.ordinal());
    }

    public boolean canPlaceStructure(int col, int row, StructureType structureType) {
        if (structureType == null || col < 0 || row < 0 || col >= width || row >= height) {
            return false;
        }
        TerrainType terrainType = getTerrainType(col, row);
        return switch (structureType) {
        case NONE -> true;
        case BRIDGE -> true;
        case FORT, WALL -> terrainType.isPassable() && !terrainType.isRiver();
        };
    }

    public double getDefenderStructureDamageMultiplier(int col, int row) {
        return getStructureType(col, row).getDefenderDamageMultiplier();
    }

    public boolean hasStructures() {
        for (int structureIndex : structures) {
            if (normalizeStructureIndex(structureIndex) != StructureType.NONE.ordinal()) {
                return true;
            }
        }
        return false;
    }

    public void setMoveCost(int col, int row, double cost) { moveCost[toIndex(col, row)] = cost; }

    public void fill(int tileIndex) {
        Arrays.fill(tiles, tileIndex);
        double cost = getDefaultMoveCost(tileIndex);
        Arrays.fill(moveCost, cost);
        Arrays.fill(structures, StructureType.NONE.ordinal());
    }

    public void fillRect(int startCol, int startRow, int rectWidth, int rectHeight, int tileIndex) {
        int endCol = Math.min(width, startCol + rectWidth);
        int endRow = Math.min(height, startRow + rectHeight);
        for (int row = Math.max(0, startRow); row < endRow; row++) {
            for (int col = Math.max(0, startCol); col < endCol; col++) {
                setTileIndex(col, row, tileIndex);
                StructureType structureType = getStructureType(col, row);
                if (!canPlaceStructure(col, row, structureType)) {
                    setStructureType(col, row, StructureType.NONE);
                }
            }
        }
    }

    public String toCsvString() {
        StringBuilder builder = new StringBuilder();
        builder.append(normalizeBattleName(battleName)).append('\n');
        builder.append(width).append(',').append(height).append('\n');
        appendTerrainRows(builder);
        if (hasStructures()) {
            appendStructureRows(builder);
        }
        return builder.toString();
    }

    private void appendTerrainRows(StringBuilder builder) {
        for (int row = 0; row < height; row++) {
            appendRow(builder, row, false);
        }
    }

    private void appendStructureRows(StringBuilder builder) {
        builder.append("structures\n");
        for (int row = 0; row < height; row++) {
            appendRow(builder, row, true);
        }
    }

    private void appendRow(StringBuilder builder, int row, boolean structureRow) {
        for (int col = 0; col < width; col++) {
            if (col > 0) {
                builder.append(',');
            }
            builder.append(structureRow ? getStructureType(col, row).ordinal() : getTileIndex(col, row));
        }
        builder.append('\n');
    }

    public boolean saveToFile(String filePath) { return PersistenceManager.saveStringToFile(filePath, toCsvString()); }

    public boolean isPassable(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return false;
        }
        StructureType structureType = getStructureType(col, row);
        return structureType.isBridge() || (getTerrainType(col, row).isPassable() && structureType.isPassable());
    }

    public double getMoveCost(int col, int row) {
        return col < 0 || row < 0 || col >= width || row >= height ? Double.POSITIVE_INFINITY
                : moveCost[row * width + col];
    }

    private static boolean isDimensionLine(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return false;
        }
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            return width > 0 && height > 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    private static String normalizeBattleName(String battleName) {
        return battleName == null || battleName.isBlank() ? DEFAULT_BATTLE_NAME : battleName.trim();
    }

    private int countAdjacentBridges(int col, int row, boolean horizontal) {
        int count = 0;
        if (horizontal) {
            count += getStructureType(col - 1, row).isBridge() ? 1 : 0;
            count += getStructureType(col + 1, row).isBridge() ? 1 : 0;
            return count;
        }
        count += getStructureType(col, row - 1).isBridge() ? 1 : 0;
        count += getStructureType(col, row + 1).isBridge() ? 1 : 0;
        return count;
    }

    private int countAdjacentRiverContinuity(int col, int row, boolean horizontal) {
        int count = 0;
        if (horizontal) {
            count += isRiverOrBridge(col - 1, row) ? 1 : 0;
            count += isRiverOrBridge(col + 1, row) ? 1 : 0;
            return count;
        }
        count += isRiverOrBridge(col, row - 1) ? 1 : 0;
        count += isRiverOrBridge(col, row + 1) ? 1 : 0;
        return count;
    }

    private boolean isRiverOrBridge(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) {
            return false;
        }
        return getTerrainType(col, row).isRiver() || getStructureType(col, row).isBridge();
    }

    private void setStructureIndex(int col, int row, int structureIndex) {
        structures[toIndex(col, row)] = normalizeStructureIndex(structureIndex);
    }

    private record CsvLayout(String battleName, int width, int height) {}

    private record CsvSource(String csvText, CsvLayout layout) {}
}
