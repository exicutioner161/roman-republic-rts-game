package test;

import campaign.CampaignManager;
import java.io.IOException;
import java.util.List;
import units.Unit;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.TileMap;

public final class TestCampaignMaps {
        private static final CampaignManager CAMPAIGN_MANAGER = new CampaignManager();

        private TestCampaignMaps() {}

        public static void main(String[] args) throws IOException {
                if (args.length > 0) {
                        System.out.println("Ignoring command-line arguments for TestCampaignMaps.");
                }
                verifyAlesiaHistoricalLayoutAndForces();
                verifyAvaricumHistoricalLayoutAndForces();
                verifyGergoviaHistoricalLayoutAndForces();
                verifyKentLandingHistoricalLayoutAndForces();
                verifyThamesCrossingHistoricalLayoutAndForces();
                verifyDyrrhachiumHistoricalLayoutAndForces();
                verifyPharsalusHistoricalLayoutAndForces();
        }

        private static void verifyAlesiaHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/alesia.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario("/resources/scenarios/caesar/alesia.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 56 && map.getHeight() == 40,
                                "Alesia should expand to a larger basin around Mont Auxois.");
                require(countTerrainInRect(map, TerrainType.RIVER, 8, 4, 14, 35) >= 18,
                                "Alesia should preserve the western river valley.");
                require(countTerrainInRect(map, TerrainType.RIVER, 41, 4, 46, 35) >= 18,
                                "Alesia should preserve the eastern river valley.");
                require(countStructuresInRect(map, StructureType.FORT, 18, 8, 38, 28) >= 20,
                                "Alesia should include the Roman inner siege line around the oppidum.");
                require(countStructuresInRect(map, StructureType.FORT, 10, 5, 46, 33) >= 40,
                                "Alesia should include the Roman outer siege line and camps.");
                require(countStructuresInRect(map, StructureType.WALL, 21, 15, 35, 21) >= 18,
                                "Alesia should preserve the oppidum wall atop Mont Auxois.");
                require(countStructuresInRect(map, StructureType.BRIDGE, 10, 17, 14, 29) >= 2,
                                "Alesia should bridge the western river crossings used by the Roman works.");
                require(countStructuresInRect(map, StructureType.BRIDGE, 42, 19, 45, 29) >= 2,
                                "Alesia should bridge the eastern river crossings used by the Roman works.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 60000,
                                "Alesia should field approximately sixty thousand Romans and allies.");
                require(sumForceSize(scenario, Unit.SoldierCulture.GALLIC) == 86000,
                                "Alesia should combine the besieged force with a larger Gallic relief army.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 15000,
                                "Alesia should give Caesar the stronger opening food reserve.");
                require(scenario.getStartingFood(Unit.SoldierCulture.GALLIC) == 5200,
                                "Alesia should keep the combined Gallic reserve materially tighter than Caesar's.");
                require("sector_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Alesia should open with a sector defense posture for the Roman ring.");
                require("relief_assault".equals(scenario.getAiDirective(Unit.SoldierCulture.GALLIC).getBehavior()),
                                "Alesia should open with a coordinated Gallic relief assault.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.GALLIC, 1100, 700, 1550,
                                980) >= 6, "Alesia should keep a substantial Gallic garrison inside the oppidum.");
                require(countForcesOutsideWorldRect(scenario.getForces(), Unit.SoldierCulture.GALLIC, 650, 450, 2100,
                                1450) >= 8, "Alesia should place most Gallic reinforcements outside the Roman ring.");
        }

        private static void verifyAvaricumHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/avaricum.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario("/resources/scenarios/caesar/avaricum.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 42 && map.getHeight() == 30,
                                "Avaricum should expand to show the marsh-bound promontory more clearly.");
                require(countWetOrRiverTilesInRect(map, 0, 0, 41, 5) >= 80,
                                "Avaricum should keep river and marsh protection across the northern face.");
                require(countWetOrRiverTilesInRect(map, 30, 2, 41, 19) >= 110,
                                "Avaricum should keep the eastern flank enclosed by river and wet ground.");
                require(map.getTerrainType(29, 10).getHeightLevel() > map.getTerrainType(9, 10).getHeightLevel(),
                                "Avaricum should keep the oppidum headland above the marshes.");
                require(countStructuresInRect(map, StructureType.FORT, 10, 18, 26, 22) >= 20,
                                "Avaricum should show Roman siege works on the single dry approach.");
                require(countStructuresInRect(map, StructureType.WALL, 21, 10, 35, 15) >= 18,
                                "Avaricum should preserve the town wall on the defended headland.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 33000,
                                "Avaricum should field roughly thirty-three thousand Romans and camp followers in combat units.");
                require(sumForceSize(scenario, Unit.SoldierCulture.GALLIC) == 40000,
                                "Avaricum should preserve the large Bituriges garrison inside the town.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 2600,
                                "Avaricum should open with a visibly tight Roman food reserve.");
                require(scenario.getStartingFood(Unit.SoldierCulture.GALLIC) == 9800,
                                "Avaricum should preserve Avaricum as the better-provisioned side at the start.");
                require("methodical_assault".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Avaricum should open with a methodical Roman assault over the dry approach.");
                require("fortified_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.GALLIC).getBehavior()),
                                "Avaricum should open with a fortified Gallic defense inside the oppidum.");
                require(allForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.GALLIC, 1200, 430, 1600, 700),
                                "Avaricum should keep the Gallic defenders concentrated inside the oppidum headland.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 120, 840, 1160,
                                1120) >= 8, "Avaricum should mass most Roman units on the western siege approach.");
        }

        private static void verifyGergoviaHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/gergovia.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario("/resources/scenarios/caesar/gergovia.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 48 && map.getHeight() == 34,
                                "Gergovia should expand to show the plateau, slopes, and Roman camps.");
                require(countTerrainInRect(map, TerrainType.IMPASSABLE_MOUNTAIN, 10, 3, 39, 14) >= 35,
                                "Gergovia should keep steep escarpments around most of the plateau edge.");
                require(countTerrainInRect(map, TerrainType.MOUNTAIN_PASS, 10, 4, 17, 16) >= 18,
                                "Gergovia should leave only a narrow practical ascent for the Roman assault.");
                require(map.getTerrainType(24, 6).getHeightLevel() > map.getTerrainType(24, 24).getHeightLevel(),
                                "Gergovia should keep the oppidum plateau above the southern plain.");
                require(countStructuresInRect(map, StructureType.WALL, 17, 6, 31, 12) >= 18,
                                "Gergovia should preserve the defensive wall line on the plateau edge.");
                require(countStructuresInRect(map, StructureType.FORT, 8, 25, 19, 30) >= 28,
                                "Gergovia should preserve Caesar's main camp on the plain.");
                require(countStructuresInRect(map, StructureType.FORT, 10, 18, 15, 21) >= 16,
                                "Gergovia should preserve the forward hill camp linked to the main position.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 33000,
                                "Gergovia should field six Roman legions with allied support.");
                require(sumForceSize(scenario, Unit.SoldierCulture.GALLIC) == 30000,
                                "Gergovia should preserve the larger Arvernian force on the heights.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 5400,
                                "Gergovia should give Caesar only a moderate opening food reserve.");
                require(scenario.getStartingFood(Unit.SoldierCulture.GALLIC) == 7600,
                                "Gergovia should give the Gallic defenders the better local provisioning.");
                require("narrow_assault".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Gergovia should open with a narrow Roman assault through the ascent corridor.");
                require("high_ground_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.GALLIC).getBehavior()),
                                "Gergovia should open with a high-ground Gallic defense on the plateau edge.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.GALLIC, 900, 300, 1650,
                                700) >= 7, "Gergovia should place the Gallic army on the plateau and camp line.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 350, 700, 1000,
                                1350) >= 7,
                                "Gergovia should keep the Roman army below the escarpment around the camps and approach.");
        }

        private static void verifyKentLandingHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/kent_landing.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario(
                                "/resources/scenarios/caesar/kent_landing.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 36 && map.getHeight() == 24,
                                "Kent Landing should keep the battle as a compact beachhead map.");
                require(countTerrainInRect(map, TerrainType.RIVER, 0, 0, 3, 23) >= 40,
                                "Kent Landing should preserve a broad surf line along the western edge.");
                require(countTerrainInRect(map, TerrainType.HILLY_GRASS, 12, 0, 22, 23) >= 120,
                                "Kent Landing should give the Britons an inland ridge above the beach.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 15200,
                                "Kent Landing should field a smaller Roman landing force.");
                require(sumForceSize(scenario, Unit.SoldierCulture.BRITON) == 16500,
                                "Kent Landing should keep a slightly larger Briton defense on the ridge.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 4200,
                                "Kent Landing should give the Romans a moderate initial supply reserve.");
                require(scenario.getStartingFood(Unit.SoldierCulture.BRITON) == 4600,
                                "Kent Landing should keep the Britons marginally better supplied at the start.");
                require("aggressive".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Kent Landing should open with a Roman drive off the shore.");
                require("high_ground_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.BRITON).getBehavior()),
                                "Kent Landing should open with Briton defense on the ridge line.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 420, 700, 800,
                                980) >= 6, "Kent Landing should keep the Roman army clustered near the beachhead.");
                require(allForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.BRITON, 940, 300, 1450, 650),
                                "Kent Landing should keep the Briton army concentrated on the inland ridge.");
        }

        private static void verifyThamesCrossingHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/thames_crossing.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario(
                                "/resources/scenarios/caesar/thames_crossing.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 38 && map.getHeight() == 26,
                                "Thames Crossing should stay focused on the defended ford and both banks.");
                require(countTerrainInRect(map, TerrainType.RIVER, 5, 10, 32, 14) >= 130,
                                "Thames Crossing should preserve the river barrier across the center of the field.");
                require(countStructuresInRect(map, StructureType.BRIDGE, 16, 10, 21, 14) >= 25,
                                "Thames Crossing should preserve a narrow engineered crossing point at the ford.");
                require(countStructuresInRect(map, StructureType.WALL, 14, 9, 23, 9) >= 8,
                                "Thames Crossing should preserve a defended stake line above the ford.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 18000,
                                "Thames Crossing should field a reinforced Roman assault column.");
                require(sumForceSize(scenario, Unit.SoldierCulture.BRITON) == 19800,
                                "Thames Crossing should give the Britons the larger river defense.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 5600,
                                "Thames Crossing should give the Romans the better logistics for a forced crossing.");
                require(scenario.getStartingFood(Unit.SoldierCulture.BRITON) == 4800,
                                "Thames Crossing should keep the Britons on a tighter local reserve.");
                require("narrow_assault".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Thames Crossing should open with a narrow Roman push across the ford.");
                require("fortified_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.BRITON).getBehavior()),
                                "Thames Crossing should open with a fortified Briton bank defense.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 420, 880, 980,
                                1120) >= 7, "Thames Crossing should mass Roman formations south of the ford.");
                require(allForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.BRITON, 700, 150, 1360, 500),
                                "Thames Crossing should keep Briton defenders on the north bank.");
        }

        private static void verifyDyrrhachiumHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/dyrrhachium.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario(
                                "/resources/scenarios/caesar/dyrrhachium.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 42 && map.getHeight() == 28,
                                "Dyrrhachium should preserve a broader siege corridor and eastern ridge system.");
                require(countTerrainInRect(map, TerrainType.IMPASSABLE_MOUNTAIN, 36, 0, 41, 23) >= 80,
                                "Dyrrhachium should keep broken high ground on the eastern side of the corridor.");
                require(countTerrainInRect(map, TerrainType.MOUNTAIN_PASS, 31, 6, 35, 17) >= 20,
                                "Dyrrhachium should preserve a few practical breaks through the ridge line.");
                require(countStructuresInRect(map, StructureType.FORT, 9, 8, 30, 13) >= 20,
                                "Dyrrhachium should preserve multiple fort nodes along the siege front.");
                require(countStructuresInRect(map, StructureType.WALL, 8, 9, 28, 12) >= 35,
                                "Dyrrhachium should preserve extended wall sections across the central corridor.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 22800,
                                "Dyrrhachium should field a smaller Caesarian assault force.");
                require(sumForceSize(scenario, Unit.SoldierCulture.POMPEIAN) == 27800,
                                "Dyrrhachium should keep Pompey with the larger Roman army.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 5200,
                                "Dyrrhachium should keep Caesar on the tighter supply base.");
                require(scenario.getStartingFood(Unit.SoldierCulture.POMPEIAN) == 8600,
                                "Dyrrhachium should give Pompey a stronger opening reserve.");
                require("methodical_assault".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Dyrrhachium should open with a careful Caesarian breach attempt.");
                require("sector_defense".equals(scenario.getAiDirective(Unit.SoldierCulture.POMPEIAN).getBehavior()),
                                "Dyrrhachium should open with a Pompeian sector defense behind the lines.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 350, 980, 1040,
                                1220) >= 8, "Dyrrhachium should keep Caesar's army massed south of the works.");
                require(allForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.POMPEIAN, 1180, 500, 1700, 930),
                                "Dyrrhachium should keep Pompey's line on the deeper eastern side of the corridor.");
        }

        private static void verifyPharsalusHistoricalLayoutAndForces() throws IOException {
                TileMap map = new TileMap("/resources/maps/caesar/pharsalus.csv");
                CampaignManager.ScenarioDefinition scenario = loadScenario("/resources/scenarios/caesar/pharsalus.csv");
                requireHistoricalNotesStayConcise(scenario);
                require(map.getWidth() == 40 && map.getHeight() == 26,
                                "Pharsalus should stay focused on the decisive open plain.");
                require(countTerrainInRect(map, TerrainType.RIVER, 0, 0, 2, 18) >= 55,
                                "Pharsalus should preserve the river flank on Caesar's left.");
                require(countTerrainInRect(map, TerrainType.FLAT_GRASS, 4, 10, 35, 22) >= 380,
                                "Pharsalus should keep most of the battlefield as open plain.");
                require(countTerrainInRect(map, TerrainType.HILLY_GRASS, 16, 0, 34, 10) >= 70,
                                "Pharsalus should give Pompey's side slightly firmer ground behind the line.");
                require(sumForceSize(scenario, Unit.SoldierCulture.ROMAN) == 22600,
                                "Pharsalus should field Caesar's compact strike force.");
                require(sumForceSize(scenario, Unit.SoldierCulture.POMPEIAN) == 26600,
                                "Pharsalus should give Pompey the larger line of battle.");
                require(scenario.getStartingFood(Unit.SoldierCulture.ROMAN) == 5000,
                                "Pharsalus should give Caesar a modest but usable reserve.");
                require(scenario.getStartingFood(Unit.SoldierCulture.POMPEIAN) == 6200,
                                "Pharsalus should keep Pompey slightly better supplied.");
                require("aggressive".equals(scenario.getAiDirective(Unit.SoldierCulture.ROMAN).getBehavior()),
                                "Pharsalus should open with an aggressive Caesarian attack.");
                require("defensive".equals(scenario.getAiDirective(Unit.SoldierCulture.POMPEIAN).getBehavior()),
                                "Pharsalus should open with Pompey waiting on the Roman advance.");
                require(countForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.ROMAN, 450, 740, 1160,
                                980) >= 8,
                                "Pharsalus should keep Caesar's army concentrated on the western half of the plain.");
                require(allForcesInWorldRect(scenario.getForces(), Unit.SoldierCulture.POMPEIAN, 1120, 500, 1700, 860),
                                "Pharsalus should keep Pompey's line on the eastern half of the plain.");
        }

        private static CampaignManager.ScenarioDefinition loadScenario(String resourcePath) {
                CampaignManager.ScenarioDefinition scenario = CAMPAIGN_MANAGER.loadScenarioDefinition(resourcePath);
                require(scenario != null, "Scenario failed to load: " + resourcePath);
                return scenario;
        }

        private static void requireHistoricalNotesStayConcise(CampaignManager.ScenarioDefinition scenario) {
                require(scenario.getHistoricalNotes() != null && scenario.getHistoricalNotes().length() <= 180,
                                "Campaign descriptions should stay concise and readable in the briefing dialog.");
        }

        private static int countTerrainInRect(TileMap map, TerrainType terrainType, int minCol, int minRow, int maxCol,
                        int maxRow) {
                int count = 0;
                for (int row = minRow; row <= maxRow; row++) {
                        for (int col = minCol; col <= maxCol; col++) {
                                if (map.getTerrainType(col, row) == terrainType) {
                                        count++;
                                }
                        }
                }
                return count;
        }

        private static int countWetOrRiverTilesInRect(TileMap map, int minCol, int minRow, int maxCol, int maxRow) {
                int count = 0;
                for (int row = minRow; row <= maxRow; row++) {
                        for (int col = minCol; col <= maxCol; col++) {
                                TerrainType terrainType = map.getTerrainType(col, row);
                                if (terrainType.isWetland() || terrainType.isRiver()) {
                                        count++;
                                }
                        }
                }
                return count;
        }

        private static int countStructuresInRect(TileMap map, StructureType structureType, int minCol, int minRow,
                        int maxCol, int maxRow) {
                int count = 0;
                for (int row = minRow; row <= maxRow; row++) {
                        for (int col = minCol; col <= maxCol; col++) {
                                if (map.getStructureType(col, row) == structureType) {
                                        count++;
                                }
                        }
                }
                return count;
        }

        private static int sumForceSize(CampaignManager.ScenarioDefinition scenario, Unit.SoldierCulture culture) {
                int total = 0;
                for (CampaignManager.ForcePlacement forcePlacement : scenario.getForces()) {
                        if (forcePlacement.getCulture() == culture) {
                                total += forcePlacement.getSize();
                        }
                }
                return total;
        }

        private static int countForcesInWorldRect(List<CampaignManager.ForcePlacement> forces,
                        Unit.SoldierCulture culture, int minX, int minY, int maxX, int maxY) {
                int count = 0;
                for (CampaignManager.ForcePlacement forcePlacement : forces) {
                        if (forcePlacement.getCulture() == culture && forcePlacement.getX() >= minX
                                        && forcePlacement.getX() <= maxX && forcePlacement.getY() >= minY
                                        && forcePlacement.getY() <= maxY) {
                                count++;
                        }
                }
                return count;
        }

        private static int countForcesOutsideWorldRect(List<CampaignManager.ForcePlacement> forces,
                        Unit.SoldierCulture culture, int minX, int minY, int maxX, int maxY) {
                int count = 0;
                for (CampaignManager.ForcePlacement forcePlacement : forces) {
                        boolean inside = forcePlacement.getX() >= minX && forcePlacement.getX() <= maxX
                                        && forcePlacement.getY() >= minY && forcePlacement.getY() <= maxY;
                        if (forcePlacement.getCulture() == culture && !inside) {
                                count++;
                        }
                }
                return count;
        }

        private static boolean allForcesInWorldRect(List<CampaignManager.ForcePlacement> forces,
                        Unit.SoldierCulture culture, int minX, int minY, int maxX, int maxY) {
                for (CampaignManager.ForcePlacement forcePlacement : forces) {
                        if (forcePlacement.getCulture() != culture) {
                                continue;
                        }
                        if (forcePlacement.getX() < minX || forcePlacement.getX() > maxX || forcePlacement.getY() < minY
                                        || forcePlacement.getY() > maxY) {
                                return false;
                        }
                }
                return true;
        }

        private static void require(boolean condition, String message) {
                if (!condition) {
                        throw new IllegalStateException(message);
                }
        }
}