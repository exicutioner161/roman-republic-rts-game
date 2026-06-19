package test;

import java.awt.Point;
import java.util.List;
import pathfinding.Pathfinder;
import units.Unit;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import units.UnitManager;
import utils.MouseHandler;
import world.GameWorld.TerrainType;
import world.TileMap;

public final class TestCollisionMovement {
    private TestCollisionMovement() {}

    public static void main(String[] args) {
        if (args.length > 0) {
            System.out.println("Ignoring command-line arguments for TestCollisionMovement.");
        }
        verifyMeleeUnitsTouchWhenEngaged();
        verifyMeleeUnitsDoNotAutoAdvanceToEngage();
        verifyExplicitEngagementKeepsTargetLocked();
        verifyMoveOrdersAllowOccupiedDestination();
        verifyMovingUnitDoesNotStickOnFriendlyBlocker();
        verifyGroupMoveTargetsStaySeparated();
        verifyStandardFormationFootprintStaysCompact();
        verifyPathfinderUsesTightFriendlyGap();
        verifyOverlappingUnitsSeparateInsteadOfSticking();
        verifyPathfindingStopsWhenStalled();
        verifyPathfindMoveRequiresReachablePath();
    }

    private static void verifyMeleeUnitsTouchWhenEngaged() {
        TileMap map = TileMap.createBlank(14, 8, TerrainType.FLAT_GRASS.ordinal());
        Unit roman = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 3 * 48, 1000, 2);
        Unit gallic = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY, 8 * 48,
                3 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(roman);
        UnitManager.addUnitForTesting(gallic);
        UnitManager.setActiveMap(map);
        double romanHealth = roman.getHealth();
        double gallicHealth = gallic.getHealth();
        roman.setEngagementTarget(gallic, map);
        gallic.setEngagementTarget(roman, map);
        boolean madeContact = false;
        for (int tick = 0; tick < 520; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
            if (roman.intersects(gallic)) {
                throw new IllegalStateException("Melee units should meet at contact without overlapping.");
            }
            if (roman.isTouching(gallic)) {
                madeContact = true;
            }
            if (madeContact && roman.getHealth() < romanHealth && gallic.getHealth() < gallicHealth) {
                break;
            }
        }
        if (!madeContact) {
            throw new IllegalStateException("Melee units should close all the way to contact before fighting.");
        }
        if (roman.getHealth() >= romanHealth || gallic.getHealth() >= gallicHealth) {
            throw new IllegalStateException(
                    "Enemy melee units should exchange damage once they have made physical contact.");
        }
    }

    private static void verifyMoveOrdersAllowOccupiedDestination() {
        TileMap map = TileMap.createBlank(14, 8, TerrainType.FLAT_GRASS.ordinal());
        Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 3 * 48, 1000, 2);
        Unit blocker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                8 * 48, 3 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(mover);
        UnitManager.addUnitForTesting(blocker);
        UnitManager.setActiveMap(map);
        Point occupiedDestination = new Point(blocker.getCoords());
        mover.setTargetCoordsComputePath(occupiedDestination, map);
        if (mover.getTargetCoords() == null || !mover.getTargetCoords().equals(occupiedDestination)) {
            throw new IllegalStateException("Occupied tiles should still be accepted as valid move targets.");
        }
        boolean reachedOccupiedTarget = false;
        for (int tick = 0; tick < 320; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
            if (mover.intersects(blocker)) {
                throw new IllegalStateException(
                        "Units ordered onto an occupied tile should stop at contact, not overlap.");
            }
            if (mover.isTouching(blocker)) {
                reachedOccupiedTarget = true;
                break;
            }
        }
        if (!reachedOccupiedTarget) {
            throw new IllegalStateException(
                    "Move orders to occupied tiles should still drive the unit all the way up to the occupant.");
        }
    }

    private static void verifyMeleeUnitsDoNotAutoAdvanceToEngage() {
        TileMap map = TileMap.createBlank(12, 8, TerrainType.FLAT_GRASS.ordinal());
        Unit roman = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 3 * 48, 1000, 2);
        Unit gallic = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY, 4 * 48,
                3 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(roman);
        UnitManager.addUnitForTesting(gallic);
        UnitManager.setActiveMap(map);
        Point romanStart = new Point(roman.getCoords());
        Point gallicStart = new Point(gallic.getCoords());
        double romanHealth = roman.getHealth();
        double gallicHealth = gallic.getHealth();
        for (int tick = 0; tick < 60; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
        }
        if (!roman.getCoords().equals(romanStart) || !gallic.getCoords().equals(gallicStart)) {
            throw new IllegalStateException(
                    "Melee units should not automatically move toward enemies without a user-issued move order.");
        }
        if (roman.getHealth() < romanHealth || gallic.getHealth() < gallicHealth) {
            throw new IllegalStateException(
                    "Melee units should not start combat from stand-off range unless the user moves them into contact.");
        }
    }

    private static void verifyExplicitEngagementKeepsTargetLocked() {
        TileMap map = TileMap.createBlank(18, 10, TerrainType.FLAT_GRASS.ordinal());
        Unit roman = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 4 * 48, 1000, 2);
        Unit primaryTarget = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY,
                8 * 48, 4 * 48, 1000, 2);
        Unit secondaryTarget = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY,
                8 * 48, 6 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(roman);
        UnitManager.addUnitForTesting(primaryTarget);
        UnitManager.addUnitForTesting(secondaryTarget);
        UnitManager.setActiveMap(map);
        double primaryStartHealth = primaryTarget.getHealth();
        double secondaryStartHealth = secondaryTarget.getHealth();
        roman.setEngagementTarget(primaryTarget, map);
        primaryTarget.setEngagementTarget(roman, map);
        secondaryTarget.setEngagementTarget(roman, map);
        for (int tick = 0; tick < 760; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
            if (primaryTarget.getHealth() < primaryStartHealth && secondaryTarget.getHealth() == secondaryStartHealth) {
                return;
            }
        }
        if (primaryTarget.getHealth() >= primaryStartHealth) {
            throw new IllegalStateException(
                    "An explicit engagement order should keep attacking the chosen target until damage is dealt.");
        }
        if (secondaryTarget.getHealth() < secondaryStartHealth) {
            throw new IllegalStateException(
                    "A unit with an explicit engagement target should not switch to a different attacker mid-fight.");
        }
    }

    private static void verifyMovingUnitDoesNotStickOnFriendlyBlocker() {
        TileMap map = TileMap.createBlank(16, 8, TerrainType.FLAT_GRASS.ordinal());
        Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 3 * 48, 1000, 2);
        Unit blocker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                6 * 48, 3 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(mover);
        UnitManager.addUnitForTesting(blocker);
        UnitManager.setActiveMap(map);
        mover.setTargetCoordsComputePath(new Point(11 * 48, 3 * 48), map);
        double startingX = mover.getX();
        boolean advancedPastBlocker = false;
        for (int tick = 0; tick < 420; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
            if (mover.intersects(blocker)) {
                throw new IllegalStateException("Units should not remain overlapped after collision resolution.");
            }
            if (mover.getX() > blocker.getX() + 12) {
                advancedPastBlocker = true;
                break;
            }
        }
        if (!advancedPastBlocker) {
            throw new IllegalStateException(
                    "Moving unit should route around a friendly blocker instead of getting stuck on collision.");
        }
        if (mover.getX() <= startingX + 48) {
            throw new IllegalStateException("Moving unit should make meaningful progress toward its destination.");
        }
    }

    private static void verifyGroupMoveTargetsStaySeparated() {
        TileMap map = TileMap.createBlank(20, 12, TerrainType.FLAT_GRASS.ordinal());
        Unit first = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 2 * 48, 1000, 2);
        Unit second = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                2 * 48, 5 * 48, 1000, 2);
        Unit third = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                4 * 48, 2 * 48, 1000, 2);
        List<Unit> group = List.of(first, second, third);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitsForTesting(group);
        UnitManager.setActiveMap(map);
        Point destination = new Point(14 * 48, 6 * 48);
        List<Point> targets = MouseHandler.resolveCompactMoveTargets(group, destination, map);
        requireDistinctTargets(targets, "Group move orders should assign distinct compact targets.");
        Point[] startPoints = new Point[group.size()];
        for (int index = 0; index < group.size(); index++) {
            Unit unit = group.get(index);
            startPoints[index] = new Point(unit.getX(), unit.getY());
            unit.setTargetCoordsComputePath(targets.get(index), map);
        }
        for (int tick = 0; tick < 520; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
            requireNoIntersections(snapshot, "Grouped units should stay separated while moving to compact targets.");
        }
        for (int index = 0; index < group.size(); index++) {
            if (group.get(index).getCoords().distance(startPoints[index]) <= 48.0) {
                throw new IllegalStateException("Grouped move orders should move each unit meaningfully.");
            }
        }
    }

    private static void verifyStandardFormationFootprintStaysCompact() {
        Unit infantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 2 * 48, 1000, 2);
        Unit cavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN, SoldierType.ROMAN_CAVALRY, 4 * 48,
                2 * 48, 1000, 2);
        if (infantry.getFormationWidth() > 48.0 * 1.12 || infantry.getFormationHeight() > 48.0 * 0.82) {
            throw new IllegalStateException(
                    "Standard infantry footprints should stay close to a single tile so nearby spacing remains believable.");
        }
        if (cavalry.getFormationWidth() > 48.0 * 1.26 || cavalry.getFormationHeight() > 48.0 * 0.92) {
            throw new IllegalStateException(
                    "Cavalry footprints can be broader than infantry, but should not drift back to oversized collision spacing.");
        }
    }

    private static void verifyPathfinderUsesTightFriendlyGap() {
        TileMap map = TileMap.createBlank(14, 7, TerrainType.FLAT_GRASS.ordinal());
        Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                1 * 48, 3 * 48, 1000, 2);
        Unit upperBlocker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                SoldierType.ROMAN_LIGHT_INFANTRY, 6 * 48, 2 * 48, 1000, 2);
        Unit lowerBlocker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                SoldierType.ROMAN_LIGHT_INFANTRY, 6 * 48, 4 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(mover);
        UnitManager.addUnitForTesting(upperBlocker);
        UnitManager.addUnitForTesting(lowerBlocker);
        UnitManager.setActiveMap(map);
        List<Point> path = Pathfinder.findPath(map, mover.getCoords(), new Point(10 * 48, 3 * 48), 48, true, mover);
        if (path.isEmpty()) {
            throw new IllegalStateException("Pathfinder should keep a route open through a non-overlapping tile gap.");
        }
        int corridorY = mover.getCoords().y;
        for (Point waypoint : path) {
            if (waypoint.y != corridorY) {
                throw new IllegalStateException(
                        "Pathfinder should use the open gap between nearby units instead of detouring around padded hitboxes.");
            }
        }
    }

    private static void verifyOverlappingUnitsSeparateInsteadOfSticking() {
        TileMap map = TileMap.createBlank(10, 6, TerrainType.FLAT_GRASS.ordinal());
        Unit first = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                3 * 48, 3 * 48, 1000, 2);
        Unit second = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY,
                3 * 48, 3 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(first);
        UnitManager.addUnitForTesting(second);
        UnitManager.setActiveMap(map);
        for (int tick = 0; tick < 2; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
        }
        if (first.intersects(second)) {
            throw new IllegalStateException(
                    "Units that begin overlapped should separate instead of remaining stuck inside each other.");
        }
        if (!first.isTouching(second)) {
            throw new IllegalStateException(
                    "Overlap resolution should separate units to contact instead of leaving an artificial gap.");
        }
    }

    private static void verifyPathfindingStopsWhenStalled() {
        TileMap map = TileMap.createBlank(14, 8, TerrainType.FLAT_GRASS.ordinal());
        Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                2 * 48, 3 * 48, 1000, 2);
        mover.setSpeed(0.0);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(mover);
        UnitManager.setActiveMap(map);
        mover.setTargetCoordsComputePath(new Point(10 * 48, 3 * 48), map);
        waitForPathfindingStallTimeout();
        Unit[] snapshot = UnitManager.getUnitsSnapshot();
        for (Unit unit : snapshot) {
            unit.update(snapshot);
        }
        if (mover.isMoving() || mover.getTargetCoords() != null) {
            throw new IllegalStateException(
                    "Pathfinding-driven movement should cancel itself after 3 seconds with no position progress.");
        }
    }

    private static void verifyPathfindMoveRequiresReachablePath() {
        TileMap map = TileMap.createBlank(7, 5, TerrainType.FLAT_GRASS.ordinal());
        for (int row = 0; row < map.getHeight(); row++) {
            map.setTerrainType(3, row, TerrainType.IMPASSABLE_MOUNTAIN);
        }
        Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                1 * 48, 2 * 48, 1000, 2);
        UnitManager.clearUnitsForTesting();
        UnitManager.addUnitForTesting(mover);
        UnitManager.setActiveMap(map);
        mover.setTargetCoordsComputePath(new Point(5 * 48, 2 * 48), map);
        for (int tick = 0; tick < 160; tick++) {
            Unit[] snapshot = UnitManager.getUnitsSnapshot();
            for (Unit unit : snapshot) {
                unit.update(snapshot);
            }
        }
        if (mover.getX() >= 3 * 48) {
            throw new IllegalStateException(
                    "Pathfinding move should not degrade into direct movement through blocked terrain.");
        }
    }

    private static void requireDistinctTargets(List<Point> targets, String message) {
        for (int index = 0; index < targets.size(); index++) {
            for (int inner = index + 1; inner < targets.size(); inner++) {
                if (targets.get(index).equals(targets.get(inner))) {
                    throw new IllegalStateException(message);
                }
            }
        }
    }

    private static void requireNoIntersections(Unit[] units, String message) {
        for (int index = 0; index < units.length; index++) {
            for (int inner = index + 1; inner < units.length; inner++) {
                if (units[index].intersects(units[inner])) {
                    throw new IllegalStateException(message);
                }
            }
        }
    }

    private static void waitForPathfindingStallTimeout() {
        try {
            Thread.sleep(3200L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the pathfinding stall timeout.", exception);
        }
    }
}