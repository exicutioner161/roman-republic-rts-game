package test;

import campaign.CampaignManager;
import java.util.LinkedHashMap;
import java.util.List;
import main.GamePanel;
import units.Unit;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import units.UnitManager;
import world.GameWorld.TerrainType;
import world.TileMap;

public final class TestSupplySystem {
    private TestSupplySystem() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestSupplySystem.");
        }
        verifySelectedForageCommandTogglesUnits();
        verifySupplyReservesAreConsumed();
        verifyForagingCollectsFood();
        verifyRecentDamageBlocksRecovery();
    }

    private static void verifySelectedForageCommandTogglesUnits() {
        GamePanel panel = new GamePanel(TileMap.createBlank(8, 8, TerrainType.FLAT_GRASS.ordinal()),
                createScenario(20,
                        new CampaignManager.ForcePlacement(SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                                2 * GamePanel.TILE_SIZE, 2 * GamePanel.TILE_SIZE, 1000, 3)));
        Unit unit = UnitManager.getUnitsSnapshot()[0];
        unit.setSelected(true);
        panel.getUnitManager().toggleSelectedForaging();
        assertTrue(unit.isForaging(), "Selected units should enter forage mode when toggled on");
        panel.getUnitManager().toggleSelectedForaging();
        assertTrue(!unit.isForaging(), "Selected units should leave forage mode when toggled off");
    }

    private static void verifySupplyReservesAreConsumed() {
        GamePanel panel = new GamePanel(TileMap.createBlank(8, 8, TerrainType.FLAT_GRASS.ordinal()),
                createScenario(12,
                        new CampaignManager.ForcePlacement(SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                                3 * GamePanel.TILE_SIZE, 3 * GamePanel.TILE_SIZE, 1000, 3)));
        UnitManager manager = panel.getUnitManager();
        Unit unit = UnitManager.getUnitsSnapshot()[0];
        int initialReserve = manager.getFoodReserve(SoldierCulture.ROMAN);
        manager.runSupplyTickForTesting();
        assertTrue(manager.getFoodReserve(SoldierCulture.ROMAN) < initialReserve,
                "A supply tick should consume food from the side reserve");
        for (int tick = 0; tick < 20 && unit.isSupplied(); tick++) {
            manager.runSupplyTickForTesting();
        }
        assertTrue(!unit.isSupplied(), "Units should eventually fall out of supply once food reserves are exhausted");
    }

    private static void verifyForagingCollectsFood() {
        GamePanel panel = new GamePanel(TileMap.createBlank(8, 8, TerrainType.FLAT_FOREST.ordinal()),
                createScenario(0,
                        new CampaignManager.ForcePlacement(SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                                4 * GamePanel.TILE_SIZE, 4 * GamePanel.TILE_SIZE, 1000, 3)));
        UnitManager manager = panel.getUnitManager();
        Unit unit = UnitManager.getUnitsSnapshot()[0];
        unit.setForaging(true);
        int initialAvailableFood = manager.getFoodReserve(SoldierCulture.ROMAN) + unit.getCarriedFood();
        for (int tick = 0; tick < 5; tick++) {
            manager.runSupplyTickForTesting();
        }
        int updatedAvailableFood = manager.getFoodReserve(SoldierCulture.ROMAN) + unit.getCarriedFood();
        assertTrue(updatedAvailableFood > initialAvailableFood,
                "Foraging should increase the total food held by the unit or its side reserve");
    }

        private static void verifyRecentDamageBlocksRecovery() {
            GamePanel panel = new GamePanel(TileMap.createBlank(8, 8, TerrainType.FLAT_GRASS.ordinal()),
            createScenario(300,
                new CampaignManager.ForcePlacement(SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                    4 * GamePanel.TILE_SIZE, 4 * GamePanel.TILE_SIZE, 1000, 3)));
            panel.getUnitManager();
        Unit unit = UnitManager.getUnitsSnapshot()[0];
        unit.setStamina((byte)64);
        unit.setMorale((byte)71);
        unit.receiveDamage(12.0);

        double blockedHealth = unit.getHealth();
        int blockedStamina = Byte.toUnsignedInt(unit.getStamina());
        int blockedMorale = Byte.toUnsignedInt(unit.getMorale());
    sleep(1100L);
        unit.update(new Unit[] {unit});

        assertTrue(Math.abs(unit.getHealth() - blockedHealth) < 1e-9,
            "Recently attacked units should not recover health during the 5 second lockout");
        assertTrue(Byte.toUnsignedInt(unit.getStamina()) == blockedStamina,
            "Recently attacked units should not recover stamina during the 5 second lockout");
        assertTrue(Byte.toUnsignedInt(unit.getMorale()) == blockedMorale,
            "Recently attacked units should not recover morale during the 5 second lockout");

    sleep(5100L);
        unit.update(new Unit[] {unit});

        assertTrue(unit.getHealth() > blockedHealth,
            "Units should be able to recover health again after the 5 second damage lockout expires");
        assertTrue(Byte.toUnsignedInt(unit.getStamina()) > blockedStamina,
            "Units should be able to recover stamina again after the 5 second damage lockout expires");
        assertTrue(Byte.toUnsignedInt(unit.getMorale()) > blockedMorale,
            "Units should be able to recover morale again after the 5 second damage lockout expires");
        }

    private static CampaignManager.ScenarioDefinition createScenario(int startingFood,
            CampaignManager.ForcePlacement forcePlacement) {
        LinkedHashMap<SoldierCulture, Integer> startingFoodByCulture = new LinkedHashMap<>();
        startingFoodByCulture.put(forcePlacement.getCulture(), startingFood);
        return new CampaignManager.ScenarioDefinition("Supply Test", "Supply Test", "custom", "", "",
                List.of(forcePlacement), startingFoodByCulture, new LinkedHashMap<>());
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for recovery-lock timing test", exception);
        }
    }
}