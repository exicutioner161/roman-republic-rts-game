package test;

import combat.CombatSystem;
import main.GamePanel;
import units.Unit;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import units.UnitManager;
import world.GameWorld.TerrainType;
import world.TileMap;

public class TestTerrainSystems {
        public static void main(String[] args) {
                if (args.length > 0) {
                        System.out.println("Ignoring command-line arguments for TestTerrainSystems.");
                }
                verifyTerrainPassabilityAndTraversal();
                verifyHighGroundRangeBonus();
                verifyElevationCombatEffects();
                verifyCavalryTerrainPerformance();
                verifyMudAndMarshTerrainEffects();
        }

        private static void verifyTerrainPassabilityAndTraversal() {
                TileMap tileMap = TileMap.createBlank("Terrain Test", 4, 4, TerrainType.FLAT_GRASS.ordinal());
                tileMap.setTerrainType(1, 1, TerrainType.IMPASSABLE_MOUNTAIN);
                tileMap.setTerrainType(2, 1, TerrainType.MOUNTAIN_PASS);
                tileMap.setTerrainType(3, 1, TerrainType.FLAT_FOREST);
                require(!tileMap.isPassable(1, 1), "Expected impassable mountains to block movement.");
                require(tileMap.isPassable(2, 1), "Expected mountain passes to remain traversable.");
                double cavalryFlat = tileMap.getTraversalCost(0, 0, SoldierClass.CAVALRY);
                double cavalryForest = tileMap.getTraversalCost(3, 1, SoldierClass.CAVALRY);
                require(cavalryFlat < cavalryForest,
                                "Expected cavalry traversal on flat grass to be faster than in flat forest.");
        }

        private static void verifyHighGroundRangeBonus() {
                TileMap tileMap = TileMap.createBlank("Range Test", 4, 4, TerrainType.FLAT_GRASS.ordinal());
                tileMap.setTerrainType(2, 2, TerrainType.HILLY_GRASS);
                UnitManager.setActiveMap(tileMap);
                Unit archerOnFlat = new Unit(null, SoldierClass.ARCHER, SoldierCulture.ROMAN, SoldierType.ROMAN_ARCHER,
                                0, 0, 1000, 2);
                Unit archerOnHill = new Unit(null, SoldierClass.ARCHER, SoldierCulture.ROMAN, SoldierType.ROMAN_ARCHER,
                                2 * GamePanel.TILE_SIZE, 2 * GamePanel.TILE_SIZE, 1000, 2);
                require(archerOnHill.getAttackRange() > archerOnFlat.getAttackRange(),
                                "Expected ranged units to gain attack range on higher terrain.");
        }

        private static void verifyElevationCombatEffects() {
                TileMap tileMap = TileMap.createBlank("Slope Test", 4, 4, TerrainType.FLAT_GRASS.ordinal());
                tileMap.setTerrainType(0, 0, TerrainType.FLAT_GRASS);
                tileMap.setTerrainType(2, 0, TerrainType.HILLY_GRASS);
                UnitManager.setActiveMap(tileMap);
                Unit lowGroundAttacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_LIGHT_INFANTRY, 0, 0, 1000, 2);
                Unit highGroundDefender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 2 * GamePanel.TILE_SIZE, 0, 1000, 2);
                double uphillDamage = CombatSystem.calculateDamage(lowGroundAttacker, highGroundDefender);
                double downhillDamage = CombatSystem.calculateDamage(highGroundDefender, lowGroundAttacker);
                require(downhillDamage > uphillDamage,
                                "Expected attacking downhill from higher terrain to outperform attacking uphill.");
        }

        private static void verifyCavalryTerrainPerformance() {
                TileMap tileMap = TileMap.createBlank("Cavalry Test", 4, 4, TerrainType.FLAT_GRASS.ordinal());
                tileMap.setTerrainType(0, 0, TerrainType.FLAT_GRASS);
                tileMap.setTerrainType(2, 0, TerrainType.HILLY_FOREST);
                tileMap.setTerrainType(3, 0, TerrainType.FLAT_GRASS);
                UnitManager.setActiveMap(tileMap);
                Unit cavalryOnFlat = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 0, 0, 1000, 2);
                Unit cavalryInForest = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 2 * GamePanel.TILE_SIZE, 0, 1000, 2);
                Unit infantryTarget = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 3 * GamePanel.TILE_SIZE, 0, 1000, 2);
                double flatDamage = CombatSystem.calculateDamage(cavalryOnFlat, infantryTarget);
                double forestDamage = CombatSystem.calculateDamage(cavalryInForest, infantryTarget);
                require(flatDamage > forestDamage,
                                "Expected cavalry to perform better on flat terrain than in forested hills.");
        }

        private static void verifyMudAndMarshTerrainEffects() {
                TileMap tileMap = TileMap.createBlank("Wet Ground Test", 5, 3, TerrainType.FLAT_GRASS.ordinal());
                tileMap.setTerrainType(1, 1, TerrainType.FLAT_MUD);
                tileMap.setTerrainType(2, 1, TerrainType.MARSHLAND);
                tileMap.setTerrainType(3, 1, TerrainType.FORESTED_MARSHLAND);
                require(tileMap.isPassable(2, 1), "Expected marshland to remain traversable.");
                double cavalryFlat = tileMap.getTraversalCost(0, 0, SoldierClass.CAVALRY);
                double cavalryMud = tileMap.getTraversalCost(1, 1, SoldierClass.CAVALRY);
                double cavalryMarsh = tileMap.getTraversalCost(2, 1, SoldierClass.CAVALRY);
                double cavalryForestedMarsh = tileMap.getTraversalCost(3, 1, SoldierClass.CAVALRY);
                require(cavalryFlat < cavalryMud, "Expected flat mud to slow cavalry compared with flat grass.");
                require(cavalryMud < cavalryMarsh && cavalryMarsh < cavalryForestedMarsh,
                                "Expected marshy ground to be progressively worse for cavalry than mud.");
                UnitManager.setActiveMap(tileMap);
                Unit cavalryOnFlat = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 0, 0, 1000, 2);
                Unit cavalryOnMud = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, GamePanel.TILE_SIZE, GamePanel.TILE_SIZE, 1000, 2);
                Unit cavalryInMarsh = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 2 * GamePanel.TILE_SIZE, GamePanel.TILE_SIZE, 1000, 2);
                Unit target = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY,
                                4 * GamePanel.TILE_SIZE, GamePanel.TILE_SIZE, 1000, 2);
                double flatDamage = CombatSystem.calculateDamage(cavalryOnFlat, target);
                double mudDamage = CombatSystem.calculateDamage(cavalryOnMud, target);
                double marshDamage = CombatSystem.calculateDamage(cavalryInMarsh, target);
                require(flatDamage > mudDamage && mudDamage > marshDamage,
                                "Expected cavalry combat performance to degrade from flat ground to mud to marsh.");
        }

        private static void require(boolean condition, String message) {
                if (!condition) {
                        throw new IllegalStateException(message);
                }
        }
}