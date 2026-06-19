package world;

import campaign.CampaignManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import main.GamePanel;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;

public class TileManager {
    private final GamePanel gp;
    private TileMap tileMap;
    private final BufferedImage[] terrainTileCache;

    public TileManager(GamePanel gp) {
        this(gp, "/resources/scenarios/sample_battle.csv", "/resources/maps/sample_map.csv");
    }

    public TileManager(GamePanel gp, TileMap initialMap) {
        this.gp = gp;
        this.terrainTileCache = createTerrainTileCache();
        this.tileMap = initialMap != null ? initialMap
                : TileMap.createBlank(GamePanel.MAX_SCREEN_COL, GamePanel.MAX_SCREEN_ROW);
    }

    public TileManager(GamePanel gp, String scenarioPath, String fallbackMapPath) {
        this.gp = gp;
        this.terrainTileCache = createTerrainTileCache();
        initializeMap(scenarioPath, fallbackMapPath);
    }

    public GamePanel getGp() { return gp; }

    public void draw(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        if (tileMap != null) {
            drawFromTileMap(g2d, visibleWorldBounds);
        } else {
            drawTiledBackground(g2d, visibleWorldBounds);
        }
    }

    private void drawFromTileMap(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        Rectangle2D bounds = visibleWorldBounds != null ? visibleWorldBounds
                : new Rectangle2D.Double(0, 0, tileMap.getWidth() * (double)GamePanel.TILE_SIZE,
                        tileMap.getHeight() * (double)GamePanel.TILE_SIZE);
        int cols = tileMap.getWidth();
        int rows = tileMap.getHeight();
        int startCol = Math.max(0, (int)Math.floor(bounds.getMinX() / GamePanel.TILE_SIZE));
        int endCol = Math.min(cols - 1, (int)Math.ceil(bounds.getMaxX() / GamePanel.TILE_SIZE));
        int startRow = Math.max(0, (int)Math.floor(bounds.getMinY() / GamePanel.TILE_SIZE));
        int endRow = Math.min(rows - 1, (int)Math.ceil(bounds.getMaxY() / GamePanel.TILE_SIZE));
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                TerrainType terrainType = tileMap.getTerrainType(c, r);
                int worldX = c * GamePanel.TILE_SIZE;
                int worldY = r * GamePanel.TILE_SIZE;
                drawTerrainTile(g2d, terrainType, worldX, worldY);
                drawStructureTile(g2d, c, r, tileMap.getStructureType(c, r), worldX, worldY);
            }
        }
    }

    private void drawTiledBackground(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        Rectangle2D bounds = visibleWorldBounds != null ? visibleWorldBounds
                : new Rectangle2D.Double(0, 0, GamePanel.SCREEN_WIDTH, GamePanel.SCREEN_HEIGHT);
        int startCol = Math.max(0, (int)Math.floor(bounds.getMinX() / GamePanel.TILE_SIZE));
        int endCol = Math.clamp((int)Math.ceil(bounds.getMaxX() / GamePanel.TILE_SIZE), startCol,
                GamePanel.MAX_SCREEN_COL - 1);
        int startRow = Math.max(0, (int)Math.floor(bounds.getMinY() / GamePanel.TILE_SIZE));
        int endRow = Math.clamp((int)Math.ceil(bounds.getMaxY() / GamePanel.TILE_SIZE), startRow,
                GamePanel.MAX_SCREEN_ROW - 1);
        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                drawTerrainTile(g2d, TerrainType.FLAT_GRASS, col * GamePanel.TILE_SIZE, row * GamePanel.TILE_SIZE);
            }
        }
    }

    private void drawTerrainTile(Graphics2D g2d, TerrainType terrainType, int x, int y) {
        BufferedImage tileImage = terrainTileCache[terrainType.ordinal()];
        if (tileImage != null) {
            g2d.drawImage(tileImage, x, y, null);
            return;
        }
        drawTerrainTileArt(g2d, terrainType, x, y);
    }

    private void drawStructureTile(Graphics2D g2d, int col, int row, StructureType structureType, int x, int y) {
        if (structureType == null || structureType == StructureType.NONE) {
            return;
        }
        int tileSize = GamePanel.TILE_SIZE;
        switch (structureType) {
        case BRIDGE -> drawBridge(g2d, col, row, x, y, tileSize);
        case FORT -> drawFort(g2d, x, y, tileSize);
        case WALL -> drawWall(g2d, col, row, x, y, tileSize);
        case NONE -> {
            // nothing to draw
        }
        }
    }

    private void drawTerrainTileArt(Graphics2D g2d, TerrainType terrainType, int x, int y) {
        Color baseColor = switch (terrainType) {
        case FLAT_GRASS -> new Color(126, 168, 88);
        case HILLY_GRASS -> new Color(108, 145, 74);
        case IMPASSABLE_MOUNTAIN -> new Color(95, 99, 110);
        case MOUNTAIN_PASS -> new Color(145, 126, 92);
        case RIVER -> new Color(68, 116, 170);
        case FLAT_FOREST -> new Color(77, 118, 58);
        case HILLY_FOREST -> new Color(67, 99, 50);
        case MOUNTAINOUS_FOREST -> new Color(72, 86, 63);
        case MARSHLAND -> new Color(105, 118, 78);
        case FORESTED_MARSHLAND -> new Color(86, 99, 64);
        case FLAT_MUD -> new Color(131, 102, 74);
        case FORESTED_MUD -> new Color(100, 88, 63);
        case HILLY_MUD -> new Color(120, 96, 70);
        };
        int tileSize = GamePanel.TILE_SIZE;
        g2d.setColor(baseColor);
        g2d.fillRect(x, y, tileSize, tileSize);
    }

    private void drawBridge(Graphics2D g2d, int col, int row, int x, int y, int tileSize) {
        boolean horizontalDeck = tileMap == null || tileMap.isBridgeDeckHorizontal(col, row);
        boolean linkWest = horizontalDeck && tileMap != null && tileMap.getStructureType(col - 1, row).isBridge();
        boolean linkEast = horizontalDeck && tileMap != null && tileMap.getStructureType(col + 1, row).isBridge();
        boolean linkNorth = !horizontalDeck && tileMap != null && tileMap.getStructureType(col, row - 1).isBridge();
        boolean linkSouth = !horizontalDeck && tileMap != null && tileMap.getStructureType(col, row + 1).isBridge();
        if (horizontalDeck) {
            StructureRenderHelper.drawBridge(g2d, x, y, tileSize, true, linkWest, linkEast);
        } else {
            StructureRenderHelper.drawBridge(g2d, x, y, tileSize, false, linkNorth, linkSouth);
        }
    }

    private void drawFort(Graphics2D g2d, int x, int y, int tileSize) {
        StructureRenderHelper.drawFort(g2d, x, y, tileSize);
    }

    private void drawWall(Graphics2D g2d, int col, int row, int x, int y, int tileSize) {
        boolean north = hasWallNeighbor(col, row - 1);
        boolean south = hasWallNeighbor(col, row + 1);
        boolean west = hasWallNeighbor(col - 1, row);
        boolean east = hasWallNeighbor(col + 1, row);
        StructureRenderHelper.drawWall(g2d, x, y, tileSize, north, south, west, east);
    }

    private boolean hasWallNeighbor(int col, int row) {
        return tileMap != null && tileMap.getStructureType(col, row).isWall();
    }

    private BufferedImage[] createTerrainTileCache() {
        BufferedImage[] cache = new BufferedImage[TerrainType.values().length];
        for (TerrainType terrainType : TerrainType.values()) {
            cache[terrainType.ordinal()] = createTerrainTileImage(terrainType);
        }
        return cache;
    }

    private BufferedImage createTerrainTileImage(TerrainType terrainType) {
        GraphicsConfiguration configuration = gp.getGraphicsConfiguration();
        int tileSize = GamePanel.TILE_SIZE;
        BufferedImage image = configuration != null ? configuration.createCompatibleImage(tileSize, tileSize)
                : new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tileGraphics = image.createGraphics();
        try {
            drawTerrainTileArt(tileGraphics, terrainType, 0, 0);
        } finally {
            tileGraphics.dispose();
        }
        return image;
    }

    public TileMap getTileMap() { return tileMap; }

    public void createBlankMap(int width, int height) { tileMap = TileMap.createBlank(width, height); }

    public void createBlankMap(int width, int height, int defaultTileIndex) {
        tileMap = TileMap.createBlank(width, height, defaultTileIndex);
    }

    public boolean saveMap(String filePath) { return tileMap != null && tileMap.saveToFile(filePath); }

    public boolean loadMap(String resourcePath) {
        try {
            tileMap = new TileMap(resourcePath);
            return true;
        } catch (IOException _) {
            return false;
        }
    }

    public boolean loadMapFile(File file) {
        TileMap loadedMap = TileMap.fromFile(file);
        if (loadedMap == null) {
            return false;
        }
        tileMap = loadedMap;
        return true;
    }

    public boolean loadScenarioMap(String scenarioPath) {
        return loadScenarioMap(scenarioPath, "/resources/maps/sample_map.csv");
    }

    public boolean loadScenarioMap(String scenarioPath, String fallbackMapPath) {
        CampaignManager campaignManager = new CampaignManager();
        String mapPath = campaignManager.loadScenarioMapPath(scenarioPath);
        if (mapPath != null && loadMap(mapPath)) {
            return true;
        }
        return loadMap(fallbackMapPath);
    }

    private void initializeMap(String scenarioPath, String fallbackMapPath) {
        loadScenarioMap(scenarioPath, fallbackMapPath);
    }
}
