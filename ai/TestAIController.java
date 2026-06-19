package ai;

import java.awt.Point;
import java.util.List;
import units.Unit;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;

public class TestAIController {
        public static void main(String[] args) {
                verifyFocusEnemyProtectsThreatenedBackline();
                verifyCavalryPrefersExposedArchersOverCloserInfantry();
                verifyThreatAssessmentPrefersCavalryPressure();
        }

        private static void verifyFocusEnemyProtectsThreatenedBackline() {
                AIController controller = new AIController(null, SoldierCulture.ROMAN);
                Unit allyInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 120, 120, 1000, 1);
                Unit allyArcher = new Unit(null, SoldierClass.ARCHER, SoldierCulture.ROMAN, SoldierType.ROMAN_ARCHER,
                                156, 192, 1000, 1);
                Unit enemyFrontline = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 214, 120, 1000, 1);
                Unit enemySupport = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 234, 140, 1000, 1);
                Unit raidingCavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_CAVALRY, 198, 188, 1000, 1);
                List<Unit> allies = List.of(allyInfantry, allyArcher);
                List<Unit> enemies = List.of(enemyFrontline, enemySupport, raidingCavalry);
                controller.setUnitsForTesting(allies, enemies);
                Unit chosen = controller.chooseFocusEnemyForTesting(computeCentroid(allies), computeCentroid(enemies));
                require(chosen == raidingCavalry,
                                "Expected focus targeting to protect the backline from a nearby cavalry threat.");
        }

        private static void verifyCavalryPrefersExposedArchersOverCloserInfantry() {
                AIController controller = new AIController(null, SoldierCulture.ROMAN);
                Unit alliedCavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 120, 120, 1000, 1);
                Unit closerInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 186, 120, 1000, 1);
                Unit exposedArcher = new Unit(null, SoldierClass.ARCHER, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_ARCHER, 222, 132, 1000, 1);
                List<Unit> allies = List.of(alliedCavalry);
                List<Unit> enemies = List.of(closerInfantry, exposedArcher);
                controller.setUnitsForTesting(allies, enemies);
                Unit chosen = controller.chooseTacticalTargetForTesting(alliedCavalry, closerInfantry);
                require(chosen == exposedArcher,
                                "Expected cavalry tactical targeting to prefer exposed archers over slightly closer infantry.");
        }

        private static void verifyThreatAssessmentPrefersCavalryPressure() {
                AIController controller = new AIController(null, SoldierCulture.PARTHIAN);
                Unit alliedHorseArcher = new Unit(null, SoldierClass.HORSE_ARCHER, SoldierCulture.PARTHIAN,
                                SoldierType.PARTHIAN_HORSE_ARCHER, 120, 120, 1000, 1);
                Unit nearbyInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 178, 120, 1000, 1);
                Unit threateningCavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_CAVALRY, 194, 128, 1000, 1);
                List<Unit> allies = List.of(alliedHorseArcher);
                List<Unit> enemies = List.of(nearbyInfantry, threateningCavalry);
                controller.setUnitsForTesting(allies, enemies);
                Unit chosen = controller.chooseThreateningEnemyForTesting(alliedHorseArcher);
                require(chosen == threateningCavalry,
                                "Expected skirmisher threat assessment to prioritize cavalry pressure over nearby infantry.");
        }

        private static Point computeCentroid(List<Unit> units) {
                double sumX = 0.0;
                double sumY = 0.0;
                for (Unit unit : units) {
                        sumX += unit.getX();
                        sumY += unit.getY();
                }
                return new Point((int)Math.round(sumX / units.size()), (int)Math.round(sumY / units.size()));
        }

        private static void require(boolean condition, String message) {
                if (!condition) {
                        throw new IllegalStateException(message);
                }
        }
}