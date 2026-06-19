package test;

import combat.CombatSystem;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import main.GamePanel;
import units.Unit;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import units.UnitManager;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.StructureRenderHelper;
import world.TileMap;

public final class TestStructures {
    private TestStructures() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestStructures.");
        }
        verifyStructureSerialization();
        verifyBridgeTraversalBonus();
        verifyWallTraversalAndDefense();
        verifyWallRendererOrientation();
        verifyFortDefenseBonus();
        verifyFortZoneEffects();
        verifyConstructionProgress();
    }

    private static void verifyStructureSerialization() {
        TileMap original = TileMap.createBlank("Structure Test", 4, 4, TerrainType.FLAT_GRASS.ordinal());
        original.setTerrainType(1, 1, TerrainType.RIVER);
        original.setStructureType(1, 1, StructureType.BRIDGE);
        original.setStructureType(2, 2, StructureType.FORT);
        original.setStructureType(0, 3, StructureType.WALL);
        TileMap loaded = TileMap.fromCsvString(original.toCsvString());
        assertEquals(StructureType.BRIDGE, loaded.getStructureType(1, 1), "Bridge should round-trip through CSV");
        assertEquals(StructureType.FORT, loaded.getStructureType(2, 2), "Fort should round-trip through CSV");
        assertEquals(StructureType.WALL, loaded.getStructureType(0, 3), "Wall should round-trip through CSV");
    }

    private static void verifyBridgeTraversalBonus() {
        TileMap map = TileMap.createBlank(3, 3, TerrainType.FLAT_GRASS.ordinal());
        map.setTerrainType(1, 1, TerrainType.RIVER);
        double riverCost = map.getTraversalCost(1, 1, SoldierClass.INFANTRY);
        map.setStructureType(1, 1, StructureType.BRIDGE);
        double bridgeCost = map.getTraversalCost(1, 1, SoldierClass.INFANTRY);
        if (bridgeCost >= riverCost) {
            throw new AssertionError("Bridge should reduce traversal cost across a river");
        }
    }

    private static void verifyWallTraversalAndDefense() {
        TileMap openMap = TileMap.createBlank(4, 2, TerrainType.FLAT_GRASS.ordinal());
        TileMap walledMap = TileMap.createBlank(4, 2, TerrainType.FLAT_GRASS.ordinal());
        walledMap.setStructureType(2, 0, StructureType.WALL);
        if (walledMap.getTraversalCost(2, 0, SoldierClass.INFANTRY) <= openMap.getTraversalCost(2, 0,
                SoldierClass.INFANTRY)) {
            throw new AssertionError("Wall should slow movement across the defended tile");
        }
        TileMap riverMap = TileMap.createBlank(3, 3, TerrainType.FLAT_GRASS.ordinal());
        riverMap.setTerrainType(1, 1, TerrainType.RIVER);
        if (riverMap.canPlaceStructure(1, 1, StructureType.WALL)) {
            throw new AssertionError("Wall should not be placeable on river tiles");
        }
        Unit attacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY, 0,
                0, 1000, 3);
        Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY,
                2 * GamePanel.TILE_SIZE, 0, 1000, 3);
        UnitManager.setActiveMap(openMap);
        double openDamage = CombatSystem.calculateDamage(attacker, defender);
        UnitManager.setActiveMap(walledMap);
        double wallDamage = CombatSystem.calculateDamage(attacker, defender);
        if (wallDamage >= openDamage) {
            throw new AssertionError("Wall should reduce incoming damage for the defender");
        }
    }

    private static void verifyWallRendererOrientation() {
        BufferedImage verticalWall = new BufferedImage(GamePanel.TILE_SIZE, GamePanel.TILE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D verticalGraphics = verticalWall.createGraphics();
        StructureRenderHelper.drawWall(verticalGraphics, 0, 0, GamePanel.TILE_SIZE, true, true, false, false);
        verticalGraphics.dispose();
        BufferedImage horizontalWall = new BufferedImage(GamePanel.TILE_SIZE, GamePanel.TILE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D horizontalGraphics = horizontalWall.createGraphics();
        StructureRenderHelper.drawWall(horizontalGraphics, 0, 0, GamePanel.TILE_SIZE, false, false, true, true);
        horizontalGraphics.dispose();
        int thickness = Math.max(4, GamePanel.TILE_SIZE / 5);
        int centerX = GamePanel.TILE_SIZE / 2 - thickness / 2;
        int centerY = GamePanel.TILE_SIZE / 2 - thickness / 2;
        int battlementDepth = Math.max(2, GamePanel.TILE_SIZE / 8);
        int verticalProbeX = centerX - Math.max(1, battlementDepth / 2);
        int verticalProbeY = GamePanel.TILE_SIZE / 4;
        int horizontalProbeX = GamePanel.TILE_SIZE / 4;
        int horizontalProbeY = centerY - Math.max(1, battlementDepth / 2);
        if (alphaAt(verticalWall, verticalProbeX, verticalProbeY) == 0) {
            throw new AssertionError("Vertical walls should render side battlements for north-south runs");
        }
        if (alphaAt(horizontalWall, verticalProbeX, verticalProbeY) != 0) {
            throw new AssertionError("Horizontal walls should not render side battlements at the vertical probe");
        }
        if (alphaAt(horizontalWall, horizontalProbeX, horizontalProbeY) == 0) {
            throw new AssertionError("Horizontal walls should render top battlements for east-west runs");
        }
        if (alphaAt(verticalWall, horizontalProbeX, horizontalProbeY) != 0) {
            throw new AssertionError("Vertical walls should not render top battlements at the horizontal probe");
        }
    }

    private static void verifyFortDefenseBonus() {
        TileMap openMap = TileMap.createBlank(4, 2, TerrainType.FLAT_GRASS.ordinal());
        TileMap fortifiedMap = TileMap.createBlank(4, 2, TerrainType.FLAT_GRASS.ordinal());
        fortifiedMap.setStructureType(2, 0, StructureType.FORT);
        Unit attacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY, 0,
                0, 1000, 3);
        Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY,
                2 * GamePanel.TILE_SIZE, 0, 1000, 3);
        UnitManager.setActiveMap(openMap);
        double openDamage = CombatSystem.calculateDamage(attacker, defender);
        UnitManager.setActiveMap(fortifiedMap);
        double fortifiedDamage = CombatSystem.calculateDamage(attacker, defender);
        if (fortifiedDamage >= openDamage) {
            throw new AssertionError("Fort should reduce incoming damage for the defender");
        }
    }

    private static void verifyFortZoneEffects() {
        TileMap map = TileMap.createBlank(7, 7, TerrainType.FLAT_GRASS.ordinal());
        map.setStructureType(3, 3, StructureType.FORT);
        double centerInfluence = map.getFortInfluenceAt(3, 3);
        double edgeInfluence = map.getFortInfluenceAt(5, 3);
        if (centerInfluence <= edgeInfluence || centerInfluence <= 0.95) {
            throw new AssertionError("Fort influence should be strongest on the fort tile");
        }
        double coverMultiplier = map.getFortMissileCoverMultiplierAtWorld(3.0 * GamePanel.TILE_SIZE,
                4.0 * GamePanel.TILE_SIZE);
        if (coverMultiplier >= 1.0) {
            throw new AssertionError("Fort zone should provide missile cover below neutral strength");
        }
    }

    private static void verifyConstructionProgress() {
        TileMap map = TileMap.createBlank(20, 15, TerrainType.FLAT_GRASS.ordinal());
        map.setTerrainType(6, 4, TerrainType.RIVER);
        GamePanel panel = new GamePanel(map, null);
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            unit.setSelected(unit.getSoldierCulture() == SoldierCulture.ROMAN);
        }
        assertTrue(!panel.hasPendingStructureBuild(), "Construction should start inactive");
        panel.setPendingStructureType(StructureType.BRIDGE);
        Point bridgePoint = new Point(6 * GamePanel.TILE_SIZE + GamePanel.TILE_SIZE / 2,
                4 * GamePanel.TILE_SIZE + GamePanel.TILE_SIZE / 2);
        if (!panel.tryBuildStructure(bridgePoint)) {
            throw new AssertionError("Starting bridge construction should succeed near selected Roman units");
        }
        panel.update();
        if (panel.getConstructionSiteCount() != 1 || panel.getConstructionProgressAt(6, 4) <= 0.0) {
            throw new AssertionError("Construction site should track build progress before completion");
        }
        for (int tick = 0; tick < 900 && map.getStructureType(6, 4) != StructureType.BRIDGE; tick++) {
            panel.update();
        }
        if (map.getStructureType(6, 4) != StructureType.BRIDGE) {
            throw new AssertionError("Bridge construction should complete after enough update ticks");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image == null ? 0 : (image.getRGB(x, y) >>> 24) & 0xFF;
    }
}