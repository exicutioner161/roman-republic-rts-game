package ai;

import campaign.CampaignManager;
import combat.CombatSystem;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import main.GamePanel;
import units.Unit;
import units.Unit.SoldierCulture;
import units.UnitManager;
import world.TileMap;

public class AIController {
    private static final long COMMAND_INTERVAL_MS = 1250;
    private static final long STATE_MIN_DURATION_MS = 2500;
    private static final double SUPPORT_RADIUS = GamePanel.TILE_SIZE * 4.5;
    private static final double FRONTLINE_STANDOFF = GamePanel.TILE_SIZE * 2.0;
    private static final double RESERVE_DEPTH = GamePanel.TILE_SIZE * 3.25;
    private static final double ARCHER_SUPPORT_DEPTH = GamePanel.TILE_SIZE * 4.0;
    private static final double FLANK_WIDTH = GamePanel.TILE_SIZE * 4.5;
    private static final double ENVELOPMENT_DEPTH = GamePanel.TILE_SIZE * 5.0;
    private static final double FEIGNED_RETREAT_DEPTH = GamePanel.TILE_SIZE * 5.5;
    private static final double FEIGNED_RETREAT_TRIGGER = GamePanel.TILE_SIZE * 4.0;
    private static final double TACTICAL_LOCAL_RADIUS = GamePanel.TILE_SIZE * 4.75;
    private final UnitManager unitManager;
    private final SoldierCulture controlled;
    private final DoctrineProfile doctrineProfile;
    private long lastCommandMs;
    private long stateEnteredMs;
    private BattleState battleState = BattleState.ADVANCE;
    private Unit focusEnemy;
    private List<Unit> enemies = new ArrayList<>();
    private List<Unit> allies = new ArrayList<>();

    public AIController(UnitManager unitManager, SoldierCulture controlled) { this(unitManager, controlled, null); }

    public AIController(UnitManager unitManager, SoldierCulture controlled, CampaignManager.AIDirective directive) {
        this.unitManager = unitManager;
        this.controlled = controlled;
        this.doctrineProfile = resolveDoctrineProfile(controlled, directive);
        this.battleState = doctrineProfile.openingState();
        this.stateEnteredMs = System.currentTimeMillis();
    }

    public void update(Unit[] unitSnapshot) {
        long now = System.currentTimeMillis();
        if (now - lastCommandMs < COMMAND_INTERVAL_MS) {
            return;
        }
        lastCommandMs = now;
        separateUnits(unitSnapshot);
        if (allies.isEmpty() || enemies.isEmpty()) {
            return;
        }
        TileMap map = unitManager.getGp().getTileManager().getTileMap();
        if (map == null) {
            return;
        }
        Point allyCenter = computeCentroid(allies);
        Point enemyCenter = computeCentroid(enemies);
        if (allyCenter == null || enemyCenter == null) {
            return;
        }
        focusEnemy = chooseFocusEnemy(allyCenter, enemyCenter);
        if (focusEnemy == null) {
            return;
        }
        battleState = updateBattleState(now, focusEnemy);
        commandAllies(map, allyCenter, enemyCenter, focusEnemy);
    }

    private void separateUnits(Unit[] unitSnapshot) {
        enemies = new ArrayList<>();
        allies = new ArrayList<>();
        for (Unit unit : unitSnapshot) {
            if (unit.hasSurrendered()) {
                continue;
            }
            if (unit.getSoldierCulture() == controlled) {
                allies.add(unit);
            } else {
                enemies.add(unit);
            }
        }
    }

    private void commandAllies(TileMap map, Point allyCenter, Point enemyCenter, Unit currentFocusEnemy) {
        List<Unit> infantry = filterByClass(allies, Unit.SoldierClass.INFANTRY);
        List<Unit> archers = filterByClass(allies, Unit.SoldierClass.ARCHER);
        List<Unit> cavalry = filterShockCavalry(allies);
        List<Unit> mobileSkirmishers = filterMobileSkirmishers(allies);
        Vec2 approach = normalizedVector(allyCenter, currentFocusEnemy.getCoords());
        Vec2 lateral = perpendicular(approach);
        double flankSign = choosePrimaryFlankSign(enemyCenter, lateral);
        double frontageDepthMultiplier = switch (battleState) {
        case HOLD -> 1.35;
        case ADVANCE -> 1.0;
        case COMMIT_RESERVE -> 0.75;
        case PURSUE_ROUT -> 0.35;
        };
        Point frontlineCenter = offsetPoint(currentFocusEnemy.getCoords(), approach,
                -FRONTLINE_STANDOFF * frontageDepthMultiplier);
        Point reservePoint = offsetPoint(frontlineCenter, approach,
                -RESERVE_DEPTH * doctrineProfile.reserveDepthMultiplier());
        Point archerLineCenter = offsetPoint(frontlineCenter, approach,
                -ARCHER_SUPPORT_DEPTH * doctrineProfile.archerDepthMultiplier());
        assignInfantry(frontlineCenter, reservePoint, approach, lateral, infantry, currentFocusEnemy, map);
        assignArchers(archerLineCenter, lateral, archers, currentFocusEnemy, map);
        assignCavalryManeuver(frontlineCenter, currentFocusEnemy, approach, lateral, cavalry, flankSign, map);
        assignMobileSkirmishers(frontlineCenter, reservePoint, currentFocusEnemy, approach, lateral, mobileSkirmishers,
                flankSign, map);
    }

    private void assignInfantry(Point frontlineCenter, Point reservePoint, Vec2 approach, Vec2 lateral,
            List<Unit> infantry, Unit currentFocusEnemy, TileMap map) {
        if (infantry.isEmpty()) {
            return;
        }
        List<Unit> orderedInfantry = getOrderedInfantry(infantry);
        Unit reserve = extractReserveUnit(orderedInfantry);
        keepReserveBackIfNeeded(reserve, reservePoint, map);
        double spacing = resolveLineSpacing(orderedInfantry, 1.05, 0.14);
        double centerIndex = (orderedInfantry.size() - 1) * 0.5;
        for (int index = 0; index < orderedInfantry.size(); index++) {
            Unit unit = orderedInfantry.get(index);
            Point linePoint = offsetPoint(frontlineCenter, lateral, (index - centerIndex) * spacing);
            orderInfantryUnit(unit, linePoint, approach, currentFocusEnemy, map);
        }
        commitReserveIfNeeded(reserve, frontlineCenter, approach, currentFocusEnemy, map);
    }

    private void assignArchers(Point archerLineCenter, Vec2 lateral, List<Unit> archers, Unit currentFocusEnemy,
            TileMap map) {
        if (archers.isEmpty()) {
            return;
        }
        double spacing = resolveLineSpacing(archers, 0.95, 0.18);
        double centerIndex = (archers.size() - 1) * 0.5;
        for (int index = 0; index < archers.size(); index++) {
            Unit archer = archers.get(index);
            Point supportPoint = offsetPoint(archerLineCenter, lateral, (index - centerIndex) * spacing);
            commandArcherUnit(archer, supportPoint, currentFocusEnemy, map);
        }
    }

    private double resolveLineSpacing(List<Unit> units, double minimumTiles, double gapTiles) {
        double spacing = GamePanel.TILE_SIZE * minimumTiles;
        double gap = GamePanel.TILE_SIZE * gapTiles;
        for (Unit unit : units) {
            spacing = Math.max(spacing, unit.getFormationWidth() + gap);
        }
        return spacing;
    }

    private void assignCavalryManeuver(Point frontlineCenter, Unit currentFocusEnemy, Vec2 approach, Vec2 lateral,
            List<Unit> cavalry, double flankSign, TileMap map) {
        if (cavalry.isEmpty()) {
            return;
        }
        int splitIndex = cavalry.size() >= 3 ? Math.max(1, cavalry.size() / 2) : cavalry.size();
        for (int index = 0; index < cavalry.size(); index++) {
            Unit rider = cavalry.get(index);
            double side = index < splitIndex ? flankSign : -flankSign;
            commandCavalryUnit(rider, frontlineCenter, currentFocusEnemy, approach, lateral, side, map);
        }
    }

    private void assignMobileSkirmishers(Point frontlineCenter, Point reservePoint, Unit currentFocusEnemy,
            Vec2 approach, Vec2 lateral, List<Unit> skirmishers, double flankSign, TileMap map) {
        if (skirmishers.isEmpty()) {
            return;
        }
        for (int index = 0; index < skirmishers.size(); index++) {
            Unit skirmisher = skirmishers.get(index);
            double side = index % 2 == 0 ? flankSign : -flankSign;
            commandSkirmisherUnit(skirmisher, frontlineCenter, reservePoint, currentFocusEnemy, approach, lateral, side,
                    map);
        }
    }

    private void commandArcherUnit(Unit archer, Point supportPoint, Unit currentFocusEnemy, TileMap map) {
        if (archer == null) {
            return;
        }
        Unit tacticalTarget = chooseTacticalTarget(archer, currentFocusEnemy);
        Point targetPoint = resolveTargetPoint(tacticalTarget, currentFocusEnemy, supportPoint);
        double commitDistance = tacticalCommitDistance(archer);
        if (battleState == BattleState.PURSUE_ROUT) {
            if (issueEngagementIfReady(archer, tacticalTarget, map, commitDistance)) {
                return;
            }
            Vec2 supportAdvance = normalizedVector(supportPoint, targetPoint);
            Point aggressiveSupportPoint = offsetPoint(supportPoint, supportAdvance,
                    GamePanel.TILE_SIZE * doctrineProfile.aggressionBias());
            issueOrderIfNeeded(archer, aggressiveSupportPoint, map);
            return;
        }
        if (issueEngagementIfReady(archer, tacticalTarget, map, commitDistance)) {
            return;
        }
        issueOrderIfNeeded(archer, supportPoint, map);
    }

    private void commandCavalryUnit(Unit rider, Point frontlineCenter, Unit currentFocusEnemy, Vec2 approach,
            Vec2 lateral, double side, TileMap map) {
        if (rider == null) {
            return;
        }
        Unit tacticalTarget = chooseTacticalTarget(rider, currentFocusEnemy);
        Point targetPoint = resolveTargetPoint(tacticalTarget, currentFocusEnemy, frontlineCenter);
        double commitDistance = tacticalCommitDistance(rider);
        Point flankAssembly = offsetPoint(frontlineCenter, lateral,
                side * FLANK_WIDTH * doctrineProfile.flankWidthMultiplier());
        Point flankAttack = offsetPoint(
                offsetPoint(targetPoint, lateral, side * FLANK_WIDTH * 0.75 * doctrineProfile.flankWidthMultiplier()),
                approach, GamePanel.TILE_SIZE * 1.5);
        Point rearAttack = offsetPoint(targetPoint, approach,
                ENVELOPMENT_DEPTH * doctrineProfile.flankWidthMultiplier());
        rearAttack = offsetPoint(rearAttack, lateral, side * GamePanel.TILE_SIZE * 1.2);
        Point current = rider.getCoords();
        if (battleState == BattleState.HOLD) {
            issueOrderIfNeeded(rider, flankAssembly, map);
            return;
        }
        if (battleState == BattleState.PURSUE_ROUT) {
            Unit pursuitTarget = choosePursuitTarget(rider);
            if (issueEngagementIfReady(rider, pursuitTarget, map, commitDistance + GamePanel.TILE_SIZE)) {
                return;
            }
            issueOrderIfNeeded(rider, pursuitTarget != null ? pursuitTarget.getCoords() : rearAttack, map);
            return;
        }
        if (current.distance(flankAssembly) > GamePanel.TILE_SIZE * 1.5) {
            issueOrderIfNeeded(rider, flankAssembly, map);
            return;
        }
        if (current.distance(flankAttack) > GamePanel.TILE_SIZE * 1.5) {
            issueOrderIfNeeded(rider, flankAttack, map);
            return;
        }
        if (issueEngagementIfReady(rider, tacticalTarget, map, commitDistance)) {
            return;
        }
        issueOrderIfNeeded(rider, rearAttack, map);
    }

    private void commandSkirmisherUnit(Unit skirmisher, Point frontlineCenter, Point reservePoint,
            Unit currentFocusEnemy, Vec2 approach, Vec2 lateral, double side, TileMap map) {
        if (skirmisher == null) {
            return;
        }
        Unit tacticalTarget = chooseTacticalTarget(skirmisher, currentFocusEnemy);
        Point targetPoint = resolveTargetPoint(tacticalTarget, currentFocusEnemy, frontlineCenter);
        double commitDistance = tacticalCommitDistance(skirmisher);
        if (shouldSkirmisherRetreat(skirmisher)) {
            issueOrderIfNeeded(skirmisher, createSkirmisherRetreatPoint(reservePoint, approach, lateral, side), map);
            return;
        }
        if (battleState == BattleState.PURSUE_ROUT) {
            Unit pursuitTarget = choosePursuitTarget(skirmisher);
            if (issueEngagementIfReady(skirmisher, pursuitTarget, map, commitDistance)) {
                return;
            }
            issueOrderIfNeeded(skirmisher, pursuitTarget != null ? pursuitTarget.getCoords()
                    : getPursuitDestination(skirmisher, currentFocusEnemy), map);
            return;
        }
        Point harassmentPoint = createHarassmentPoint(targetPoint, approach, lateral, side);
        if (issueEngagementIfReady(skirmisher, tacticalTarget, map, commitDistance)) {
            return;
        }
        issueOrderIfNeeded(skirmisher, harassmentPoint, map);
    }

    private List<Unit> getOrderedInfantry(List<Unit> infantry) {
        List<Unit> orderedInfantry = new ArrayList<>(infantry);
        orderedInfantry.sort(Comparator.comparingDouble(this::frontlineValue).reversed());
        return orderedInfantry;
    }

    private Unit extractReserveUnit(List<Unit> orderedInfantry) {
        return orderedInfantry.size() >= 3 ? orderedInfantry.remove(0) : null;
    }

    private void keepReserveBackIfNeeded(Unit reserve, Point reservePoint, TileMap map) {
        if (reserve == null || battleState == BattleState.COMMIT_RESERVE || battleState == BattleState.PURSUE_ROUT) {
            return;
        }
        issueOrderIfNeeded(reserve, reservePoint, map);
    }

    private void orderInfantryUnit(Unit unit, Point linePoint, Vec2 approach, Unit currentFocusEnemy, TileMap map) {
        if (unit == null) {
            return;
        }
        Unit tacticalTarget = chooseTacticalTarget(unit, currentFocusEnemy);
        double commitDistance = tacticalCommitDistance(unit);
        if (battleState == BattleState.HOLD) {
            if (issueEngagementIfReady(unit, tacticalTarget, map, commitDistance)) {
                return;
            }
            issueOrderIfNeeded(unit, linePoint, map);
            return;
        }
        if (battleState == BattleState.PURSUE_ROUT) {
            Unit pursuitTarget = choosePursuitTarget(unit);
            if (issueEngagementIfReady(unit, pursuitTarget, map, commitDistance)) {
                return;
            }
            issueOrderIfNeeded(unit, resolveTargetPoint(pursuitTarget, currentFocusEnemy, linePoint), map);
            return;
        }
        if (issueEngagementIfReady(unit, tacticalTarget, map, commitDistance)) {
            return;
        }
        Point targetPoint = resolveTargetPoint(tacticalTarget, currentFocusEnemy, linePoint);
        if (unit.getCoords().distance(targetPoint) <= unit.getAttackRange() * 0.75) {
            issueOrderIfNeeded(unit, linePoint, map);
            return;
        }
        double pushDepth = battleState == BattleState.COMMIT_RESERVE ? GamePanel.TILE_SIZE * 1.35
                : GamePanel.TILE_SIZE * 0.75;
        Point assaultPoint = offsetPoint(linePoint, approach, pushDepth);
        issueOrderIfNeeded(unit, assaultPoint, map);
    }

    private void commitReserveIfNeeded(Unit reserve, Point frontlineCenter, Vec2 approach, Unit currentFocusEnemy,
            TileMap map) {
        if (reserve == null || (battleState != BattleState.COMMIT_RESERVE && battleState != BattleState.PURSUE_ROUT)) {
            return;
        }
        Point reserveCommitPoint = battleState == BattleState.PURSUE_ROUT
                ? getPursuitDestination(reserve, currentFocusEnemy)
                : offsetPoint(frontlineCenter, approach, GamePanel.TILE_SIZE * 1.25);
        issueOrderIfNeeded(reserve, reserveCommitPoint, map);
    }

    private boolean shouldSkirmisherRetreat(Unit skirmisher) {
        Unit pursuer = chooseThreateningEnemy(skirmisher);
        boolean pressured = pursuer != null && skirmisher.getCoords()
                .distance(pursuer.getCoords()) < FEIGNED_RETREAT_TRIGGER * doctrineProfile.skirmishBias();
        boolean shaky = Byte.toUnsignedInt(skirmisher.getMorale()) < 55
                || Byte.toUnsignedInt(skirmisher.getCohesion()) < 55;
        return pressured || shaky;
    }

    private Point createSkirmisherRetreatPoint(Point reservePoint, Vec2 approach, Vec2 lateral, double side) {
        Point retreatPoint = offsetPoint(reservePoint, lateral, side * GamePanel.TILE_SIZE * 2.0);
        return offsetPoint(retreatPoint, approach, -FEIGNED_RETREAT_DEPTH * doctrineProfile.skirmishBias());
    }

    private Point createHarassmentPoint(Point frontlineCenter, Vec2 approach, Vec2 lateral, double side) {
        Point harassmentPoint = offsetPoint(frontlineCenter, lateral,
                side * (FLANK_WIDTH * 0.8 * doctrineProfile.flankWidthMultiplier()));
        return offsetPoint(harassmentPoint, approach, -GamePanel.TILE_SIZE * 0.5);
    }

    private Point getPursuitDestination(Unit unit, Unit currentFocusEnemy) {
        Unit pursuitTarget = choosePursuitTarget(unit);
        return pursuitTarget != null ? pursuitTarget.getCoords()
                : resolveTargetPoint(null, currentFocusEnemy, unit.getCoords());
    }

    private Unit chooseFocusEnemy(Point allyCenter, Point enemyCenter) {
        Unit best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Unit enemy : enemies) {
            double support = countSupport(enemy, enemies, SUPPORT_RADIUS);
            double isolationScore = 4.0 - Math.min(4.0, support);
            double weaknessScore = (1.0 - enemy.getHealth() / Math.max(1.0, enemy.getMaxHealth())) * 2.0;
            double moraleScore = (100.0 - Byte.toUnsignedInt(enemy.getMorale())) / 40.0;
            double wingExposure = enemy.getCoords().distance(enemyCenter) / (GamePanel.TILE_SIZE * 3.0);
            double approachDistance = allyCenter.distance(enemy.getCoords()) / (GamePanel.TILE_SIZE * 8.0);
            double localPressure = countSupport(enemy, allies, TACTICAL_LOCAL_RADIUS)
                    - countSupport(enemy, enemies, TACTICAL_LOCAL_RADIUS) * 0.8;
            double alliedThreatScore = threatenedAlliedValueNear(enemy);
            double targetValue = focusTargetValue(enemy);
            double score = isolationScore + weaknessScore + moraleScore + wingExposure + doctrineTargetPriority(enemy)
                    + localPressure * 0.45 + alliedThreatScore * 0.30 + targetValue - approachDistance;
            if (enemy == focusEnemy) {
                score += 0.35;
            }
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private BattleState updateBattleState(long now, Unit currentFocusEnemy) {
        BattleState desiredState = determineBattleState(currentFocusEnemy);
        if (desiredState == battleState) {
            return battleState;
        }
        if (desiredState != BattleState.PURSUE_ROUT && now - stateEnteredMs < STATE_MIN_DURATION_MS) {
            return battleState;
        }
        battleState = desiredState;
        stateEnteredMs = now;
        return battleState;
    }

    private BattleState determineBattleState(Unit currentFocusEnemy) {
        double alliedStrength = estimateGroupStrength(allies);
        double enemyStrength = estimateGroupStrength(enemies);
        double alliedDisorder = estimateGroupDisorder(allies);
        double enemyDisorder = estimateGroupDisorder(enemies);
        boolean enemyBreaking = enemyDisorder > 0.60
                || enemyStrength < alliedStrength * (0.62 + doctrineProfile.pursuitBias() * 0.04);
        if (enemyBreaking) {
            return BattleState.PURSUE_ROUT;
        }
        boolean weakenedFocus = currentFocusEnemy != null
                && (currentFocusEnemy.getHealth() / Math.max(1.0, currentFocusEnemy.getMaxHealth()) < 0.72
                        || Byte.toUnsignedInt(currentFocusEnemy.getMorale()) < 55);
        boolean reserveReady = countReadyReserveInfantry() > 0;
        if (reserveReady && weakenedFocus && alliedStrength >= enemyStrength * doctrineProfile.reserveCommitRatio()) {
            return BattleState.COMMIT_RESERVE;
        }
        if (alliedDisorder > enemyDisorder + doctrineProfile.holdBias()
                || alliedStrength < enemyStrength * doctrineProfile.holdStrengthRatio()) {
            return BattleState.HOLD;
        }
        return BattleState.ADVANCE;
    }

    private double choosePrimaryFlankSign(Point enemyCenter, Vec2 lateral) {
        double positiveStrength = 0.0;
        double negativeStrength = 0.0;
        for (Unit enemy : enemies) {
            double projection = (enemy.getX() - enemyCenter.x) * lateral.x + (enemy.getY() - enemyCenter.y) * lateral.y;
            double weight = enemy.getUnitSize() / 1000.0 + classWeight(enemy);
            if (projection >= 0) {
                positiveStrength += weight;
            } else {
                negativeStrength += weight;
            }
        }
        return positiveStrength <= negativeStrength ? 1.0 : -1.0;
    }

    private List<Unit> filterByClass(List<Unit> units, Unit.SoldierClass soldierClass) {
        List<Unit> matches = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.getSoldierClass() == soldierClass) {
                matches.add(unit);
            }
        }
        return matches;
    }

    private List<Unit> filterShockCavalry(List<Unit> units) {
        List<Unit> matches = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.getSoldierClass() == Unit.SoldierClass.CAVALRY
                    && unit.getSoldierType() != Unit.SoldierType.EGYPTIAN_CHARIOT) {
                matches.add(unit);
            }
        }
        return matches;
    }

    private List<Unit> filterMobileSkirmishers(List<Unit> units) {
        List<Unit> matches = new ArrayList<>();
        for (Unit unit : units) {
            if (unit.getSoldierClass() == Unit.SoldierClass.HORSE_ARCHER
                    || unit.getSoldierType() == Unit.SoldierType.EGYPTIAN_CHARIOT) {
                matches.add(unit);
            }
        }
        return matches;
    }

    private double countSupport(Unit origin, List<Unit> pool, double radius) {
        double support = 0.0;
        for (Unit unit : pool) {
            if (unit == origin) {
                continue;
            }
            if (origin.getCoords().distance(unit.getCoords()) <= radius) {
                support += classWeight(unit);
            }
        }
        return support;
    }

    private double classWeight(Unit unit) {
        return switch (unit.getSoldierClass()) {
        case INFANTRY -> 1.3;
        case CAVALRY -> 1.2;
        case ARCHER -> 0.8;
        case HORSE_ARCHER -> 1.0;
        };
    }

    private double doctrineTargetPriority(Unit enemy) {
        return switch (doctrineProfile.doctrine()) {
        case ROMAN_DISCIPLINED -> switch (enemy.getSoldierClass()) {
        case INFANTRY -> 1.35;
        case CAVALRY -> 1.15;
        case HORSE_ARCHER -> 1.05;
        case ARCHER -> 0.90;
        };
        case GALLIC_ASSAULT -> switch (enemy.getSoldierClass()) {
        case ARCHER -> 1.20;
        case INFANTRY -> 1.15;
        case CAVALRY, HORSE_ARCHER -> 1.00;
        };
        case PARTHIAN_SKIRMISH -> switch (enemy.getSoldierClass()) {
        case INFANTRY -> 1.30;
        case ARCHER -> 1.10;
        case CAVALRY -> 0.95;
        case HORSE_ARCHER -> 0.85;
        };
        case EGYPTIAN_BALANCED -> switch (enemy.getSoldierClass()) {
        case INFANTRY -> 1.20;
        case ARCHER -> 1.05;
        case CAVALRY, HORSE_ARCHER -> 1.00;
        };
        };
    }

    private double frontlineValue(Unit unit) {
        return unit.getHealth() + Byte.toUnsignedInt(unit.getCohesion()) * 0.7
                + Byte.toUnsignedInt(unit.getMorale()) * 0.5;
    }

    private Unit choosePursuitTarget(Unit ally) {
        Unit best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Unit enemy : enemies) {
            double distancePenalty = ally.getCoords().distance(enemy.getCoords()) / (GamePanel.TILE_SIZE * 6.0);
            double routScore = (100.0 - Byte.toUnsignedInt(enemy.getMorale())) / 30.0;
            double damageScore = (1.0 - enemy.getHealth() / Math.max(1.0, enemy.getMaxHealth())) * 2.0;
            double classPriority = enemy.getSoldierClass() == Unit.SoldierClass.ARCHER ? 0.5 : 0.0;
            double score = routScore + damageScore + classPriority - distancePenalty;
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private Unit chooseTacticalTarget(Unit ally, Unit fallbackEnemy) {
        if (ally == null || enemies.isEmpty()) {
            return fallbackEnemy;
        }
        Unit best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Unit enemy : enemies) {
            double score = scoreTacticalTarget(ally, enemy, fallbackEnemy);
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best != null ? best : fallbackEnemy;
    }

    private double scoreTacticalTarget(Unit ally, Unit enemy, Unit fallbackEnemy) {
        double distancePenalty = ally.getCoords().distance(enemy.getCoords())
                / (GamePanel.TILE_SIZE * tacticalDistanceScale(ally));
        double matchupScore = (CombatSystem.calculateDamage(ally, enemy) - CombatSystem.calculateDamage(enemy, ally))
                * 0.55;
        double localAdvantage = (countSupport(enemy, allies, TACTICAL_LOCAL_RADIUS)
                - countSupport(enemy, enemies, TACTICAL_LOCAL_RADIUS)) * 0.35;
        double weaknessScore = (1.0 - enemy.getHealth() / Math.max(1.0, enemy.getMaxHealth())) * 1.8
                + (100.0 - Byte.toUnsignedInt(enemy.getMorale())) / 55.0
                + (100.0 - Byte.toUnsignedInt(enemy.getCohesion())) / 65.0;
        double pressureScore = threatenedAlliedValueNear(enemy) * 0.45;
        double rolePriority = tacticalRolePriority(ally, enemy);
        double score = matchupScore + localAdvantage + weaknessScore + pressureScore + doctrineTargetPriority(enemy)
                + rolePriority - distancePenalty;
        if (enemy == fallbackEnemy) {
            score += 0.65;
        }
        return score;
    }

    private Unit chooseThreateningEnemy(Unit ally) {
        if (ally == null || enemies.isEmpty()) {
            return null;
        }
        Unit best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Unit enemy : enemies) {
            double proximityScore = Math.max(0.0,
                    2.5 - ally.getCoords().distance(enemy.getCoords()) / Math.max(1.0, FEIGNED_RETREAT_TRIGGER));
            double damageThreat = CombatSystem.calculateDamage(enemy, ally) * 0.45;
            double mobilityThreat = switch (enemy.getSoldierClass()) {
            case CAVALRY -> 1.15;
            case HORSE_ARCHER -> 0.90;
            case INFANTRY -> 0.45;
            case ARCHER -> 0.20;
            };
            double localSupport = countSupport(enemy, enemies, TACTICAL_LOCAL_RADIUS) * 0.18;
            double score = proximityScore + damageThreat + mobilityThreat + localSupport;
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private double threatenedAlliedValueNear(Unit enemy) {
        double threatenedValue = 0.0;
        for (Unit ally : allies) {
            double distance = ally.getCoords().distance(enemy.getCoords());
            if (distance > TACTICAL_LOCAL_RADIUS) {
                continue;
            }
            double vulnerability = switch (ally.getSoldierClass()) {
            case ARCHER -> 1.30;
            case HORSE_ARCHER -> 1.10;
            case CAVALRY -> 0.95;
            case INFANTRY -> 0.80;
            };
            threatenedValue += vulnerability * (1.0 - distance / TACTICAL_LOCAL_RADIUS);
        }
        return threatenedValue;
    }

    private double focusTargetValue(Unit enemy) {
        return switch (enemy.getSoldierClass()) {
        case ARCHER -> 1.35;
        case HORSE_ARCHER -> 1.05;
        case CAVALRY -> 0.85;
        case INFANTRY -> 0.60;
        };
    }

    private double tacticalRolePriority(Unit ally, Unit enemy) {
        return switch (ally.getSoldierClass()) {
        case INFANTRY -> switch (enemy.getSoldierClass()) {
        case CAVALRY -> 0.80;
        case INFANTRY -> 0.55;
        case HORSE_ARCHER -> 0.20;
        case ARCHER -> -0.05;
        };
        case ARCHER -> switch (enemy.getSoldierClass()) {
        case INFANTRY -> 0.90;
        case HORSE_ARCHER -> 0.45;
        case ARCHER -> 0.20;
        case CAVALRY -> -0.30;
        };
        case CAVALRY -> switch (enemy.getSoldierClass()) {
        case ARCHER -> 1.05;
        case HORSE_ARCHER -> 0.75;
        case CAVALRY -> 0.35;
        case INFANTRY -> 0.10;
        };
        case HORSE_ARCHER -> switch (enemy.getSoldierClass()) {
        case INFANTRY -> 0.80;
        case ARCHER -> 0.35;
        case HORSE_ARCHER -> 0.15;
        case CAVALRY -> -0.10;
        };
        };
    }

    private double tacticalDistanceScale(Unit ally) {
        return switch (ally.getSoldierClass()) {
        case INFANTRY -> 6.0;
        case ARCHER -> 7.2;
        case CAVALRY -> 8.4;
        case HORSE_ARCHER -> 9.0;
        };
    }

    private double tacticalCommitDistance(Unit unit) {
        if (unit == null) {
            return 0.0;
        }
        return switch (unit.getSoldierClass()) {
        case INFANTRY -> GamePanel.TILE_SIZE * 3.0;
        case ARCHER -> unit.getAttackRange() * 1.05;
        case CAVALRY -> GamePanel.TILE_SIZE * 4.25;
        case HORSE_ARCHER -> unit.getAttackRange() * 0.95;
        };
    }

    private Point resolveTargetPoint(Unit tacticalTarget, Unit fallbackTarget, Point fallbackPoint) {
        if (tacticalTarget != null) {
            return tacticalTarget.getCoords();
        }
        if (fallbackTarget != null) {
            return fallbackTarget.getCoords();
        }
        return fallbackPoint;
    }

    private boolean issueEngagementIfReady(Unit unit, Unit target, TileMap map, double maxDistance) {
        if (unit == null || target == null || map == null || unit.hasSurrendered() || target.hasSurrendered()) {
            return false;
        }
        if (unit.getCoords().distance(target.getCoords()) > maxDistance) {
            return false;
        }
        if (unit.getEngagementTarget() == target) {
            return true;
        }
        unit.setEngagementTarget(target, map);
        return true;
    }

    private double estimateGroupStrength(List<Unit> units) {
        double strength = 0.0;
        for (Unit unit : units) {
            double sizeWeight = Math.max(1.0, unit.getUnitSize() / 1000.0);
            double condition = (Byte.toUnsignedInt(unit.getMorale()) + Byte.toUnsignedInt(unit.getCohesion())
                    + Byte.toUnsignedInt(unit.getStamina())) / 300.0;
            strength += sizeWeight * (0.7 + condition * 0.6) * classWeight(unit);
        }
        return strength;
    }

    private double estimateGroupDisorder(List<Unit> units) {
        if (units.isEmpty()) {
            return 0.0;
        }
        double disorder = 0.0;
        for (Unit unit : units) {
            double moraleLoss = 1.0 - Byte.toUnsignedInt(unit.getMorale()) / 100.0;
            double cohesionLoss = 1.0 - Byte.toUnsignedInt(unit.getCohesion()) / 100.0;
            double staminaLoss = 1.0 - Byte.toUnsignedInt(unit.getStamina()) / 100.0;
            disorder += moraleLoss * 0.45 + cohesionLoss * 0.35 + staminaLoss * 0.20;
        }
        return disorder / units.size();
    }

    private int countReadyReserveInfantry() {
        int ready = 0;
        for (Unit unit : allies) {
            if (unit.getSoldierClass() != Unit.SoldierClass.INFANTRY) {
                continue;
            }
            if (Byte.toUnsignedInt(unit.getMorale()) >= 60 && Byte.toUnsignedInt(unit.getCohesion()) >= 60
                    && Byte.toUnsignedInt(unit.getStamina()) >= 50) {
                ready++;
            }
        }
        return ready;
    }

    private Point computeCentroid(List<Unit> units) {
        if (units.isEmpty()) {
            return null;
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double totalWeight = 0.0;
        for (Unit unit : units) {
            double weight = Math.max(1.0, unit.getUnitSize() / 1000.0);
            sumX += unit.getX() * weight;
            sumY += unit.getY() * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0) {
            return null;
        }
        return new Point((int)Math.round(sumX / totalWeight), (int)Math.round(sumY / totalWeight));
    }

    private Point offsetPoint(Point origin, Vec2 direction, double distance) {
        return new Point((int)Math.round(origin.x + direction.x * distance),
                (int)Math.round(origin.y + direction.y * distance));
    }

    private Vec2 normalizedVector(Point from, Point to) {
        double dx = to.x * 1.0 - from.x;
        double dy = to.y * 1.0 - from.y;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new Vec2(1.0, 0.0);
        }
        return new Vec2(dx / len, dy / len);
    }

    private Vec2 perpendicular(Vec2 vector) { return new Vec2(-vector.y, vector.x); }

    private void issueOrderIfNeeded(Unit unit, Point target, TileMap map) {
        if (unit == null || target == null || unit.hasSurrendered()) {
            return;
        }
        Point clamped = clampToMap(target, map);
        Point currentTarget = unit.getTargetCoords();
        if (currentTarget != null && currentTarget.distance(clamped) < GamePanel.TILE_SIZE * 0.75 && unit.isMoving()) {
            return;
        }
        if (!unit.isMoving() && unit.getCoords().distance(clamped) < GamePanel.TILE_SIZE * 0.5) {
            return;
        }
        unit.setTargetCoordsComputePath(clamped, map);
    }

    private Point clampToMap(Point point, TileMap map) {
        int maxX = Math.max(GamePanel.TILE_SIZE, map.getWidth() * GamePanel.TILE_SIZE - GamePanel.TILE_SIZE);
        int maxY = Math.max(GamePanel.TILE_SIZE, map.getHeight() * GamePanel.TILE_SIZE - GamePanel.TILE_SIZE);
        return new Point(Math.clamp(point.x, GamePanel.TILE_SIZE, maxX),
                Math.clamp(point.y, GamePanel.TILE_SIZE, maxY));
    }

    public List<Unit> getEnemies() { return enemies; }

    public List<Unit> getAllies() { return allies; }

    void setUnitsForTesting(List<Unit> alliedUnits, List<Unit> enemyUnits) {
        allies = new ArrayList<>(alliedUnits);
        enemies = new ArrayList<>(enemyUnits);
        focusEnemy = null;
    }

    Unit chooseFocusEnemyForTesting(Point allyCenter, Point enemyCenter) {
        return chooseFocusEnemy(allyCenter, enemyCenter);
    }

    Unit chooseTacticalTargetForTesting(Unit ally, Unit fallbackEnemy) {
        return chooseTacticalTarget(ally, fallbackEnemy);
    }

    Unit chooseThreateningEnemyForTesting(Unit ally) { return chooseThreateningEnemy(ally); }

    private DoctrineProfile resolveDoctrineProfile(SoldierCulture culture, CampaignManager.AIDirective directive) {
        Doctrine doctrine = doctrineFrom(culture, directive != null ? directive.getDoctrine() : null);
        DoctrineProfile baseProfile = switch (doctrine) {
        case ROMAN_DISCIPLINED -> new DoctrineProfile(doctrine, 1.0, 0.92, 0.90, 0.95, 0.85, 0.20, 1.0, 1.0,
                BattleState.ADVANCE);
        case GALLIC_ASSAULT -> new DoctrineProfile(doctrine, 1.25, 0.82, 0.78, 1.15, 1.15, 0.12, 0.95, 1.0,
                BattleState.ADVANCE);
        case PARTHIAN_SKIRMISH -> new DoctrineProfile(doctrine, 0.90, 0.98, 0.92, 1.30, 0.95, 0.24, 1.15, 1.15,
                BattleState.ADVANCE);
        case EGYPTIAN_BALANCED -> new DoctrineProfile(doctrine, 1.05, 0.90, 0.86, 1.05, 0.98, 0.18, 1.05, 1.05,
                BattleState.ADVANCE);
        };
        if (directive == null || directive.getBehavior().isBlank()) {
            return baseProfile;
        }
        String behavior = directive.getBehavior().trim().toLowerCase().replace(' ', '_').replace('-', '_');
        return switch (behavior) {
        case "aggressive" -> baseProfile.withTuning(0.15, -0.06, -0.05, 0.10, 0.10, -0.04, 0.00, 0.00);
        case "defensive" -> baseProfile.withTuning(-0.10, 0.08, 0.05, -0.05, -0.05, 0.08, 0.05, 0.08)
                .withOpeningState(BattleState.HOLD);
        case "skirmish", "mobile" -> baseProfile.withTuning(-0.05, 0.04, 0.02, 0.15, 0.00, 0.04, 0.00, 0.00);
        case "sector_defense", "ring_defense" -> baseProfile
                .withTuning(-0.12, 0.10, 0.08, -0.12, -0.05, 0.12, 0.08, 0.16).withOpeningState(BattleState.HOLD);
        case "relief_assault" -> baseProfile.withTuning(0.22, -0.08, -0.07, 0.20, 0.16, -0.06, -0.03, -0.04)
                .withOpeningState(BattleState.ADVANCE);
        case "siege_assault", "methodical_assault" -> baseProfile
                .withTuning(0.05, -0.02, -0.03, -0.14, -0.04, 0.04, -0.06, 0.12).withOpeningState(BattleState.ADVANCE);
        case "fortified_defense" -> baseProfile.withTuning(-0.16, 0.12, 0.10, -0.18, -0.10, 0.14, 0.10, 0.18)
                .withOpeningState(BattleState.HOLD);
        case "narrow_assault" -> baseProfile.withTuning(0.10, -0.04, -0.02, -0.20, -0.02, 0.02, -0.05, 0.10)
                .withOpeningState(BattleState.ADVANCE);
        case "high_ground_defense" -> baseProfile.withTuning(-0.12, 0.10, 0.08, -0.12, -0.06, 0.10, 0.04, 0.16)
                .withOpeningState(BattleState.HOLD);
        default -> baseProfile;
        };
    }

    private Doctrine doctrineFrom(SoldierCulture culture, String doctrineName) {
        if (doctrineName != null && !doctrineName.isBlank()) {
            String normalized = doctrineName.trim().toLowerCase().replace(' ', '_').replace('-', '_');
            return switch (normalized) {
            case "roman", "roman_disciplined", "disciplined" -> Doctrine.ROMAN_DISCIPLINED;
            case "gallic", "gallic_assault", "aggressive_assault", "briton", "briton_tribal", "tribal_assault" -> Doctrine.GALLIC_ASSAULT;
            case "parthian", "parthian_skirmish", "skirmish" -> Doctrine.PARTHIAN_SKIRMISH;
            case "egyptian", "egyptian_balanced", "balanced" -> Doctrine.EGYPTIAN_BALANCED;
            case "pompeian", "civil_war_roman", "senatorial" -> Doctrine.ROMAN_DISCIPLINED;
            default -> defaultDoctrineForCulture(culture);
            };
        }
        return defaultDoctrineForCulture(culture);
    }

    private Doctrine defaultDoctrineForCulture(SoldierCulture culture) {
        return switch (culture) {
        case ROMAN -> Doctrine.ROMAN_DISCIPLINED;
        case GALLIC -> Doctrine.GALLIC_ASSAULT;
        case PARTHIAN -> Doctrine.PARTHIAN_SKIRMISH;
        case EGYPTIAN -> Doctrine.EGYPTIAN_BALANCED;
        case BRITON -> Doctrine.GALLIC_ASSAULT;
        case POMPEIAN -> Doctrine.ROMAN_DISCIPLINED;
        };
    }

    private record Vec2(double x, double y) {}

    private record DoctrineProfile(Doctrine doctrine, double aggressionBias, double holdStrengthRatio,
            double reserveCommitRatio, double flankWidthMultiplier, double pursuitBias, double holdBias,
            double archerDepthMultiplier, double reserveDepthMultiplier, BattleState openingState) {
        private DoctrineProfile withTuning(double aggressionDelta, double holdStrengthDelta, double reserveCommitDelta,
                double flankDelta, double pursuitDelta, double holdBiasDelta, double archerDepthDelta,
                double reserveDepthDelta) {
            return new DoctrineProfile(doctrine, aggressionBias + aggressionDelta,
                    holdStrengthRatio + holdStrengthDelta, reserveCommitRatio + reserveCommitDelta,
                    flankWidthMultiplier + flankDelta, pursuitBias + pursuitDelta, holdBias + holdBiasDelta,
                    archerDepthMultiplier + archerDepthDelta, reserveDepthMultiplier + reserveDepthDelta, openingState);
        }

        private DoctrineProfile withOpeningState(BattleState nextOpeningState) {
            return new DoctrineProfile(doctrine, aggressionBias, holdStrengthRatio, reserveCommitRatio,
                    flankWidthMultiplier, pursuitBias, holdBias, archerDepthMultiplier, reserveDepthMultiplier,
                    nextOpeningState);
        }

        private double skirmishBias() {
            return Math.clamp(flankWidthMultiplier + (doctrine == Doctrine.PARTHIAN_SKIRMISH ? 0.15 : 0.0), 0.85, 1.45);
        }
    }

    private enum BattleState {
        ADVANCE, HOLD, COMMIT_RESERVE, PURSUE_ROUT
    }

    private enum Doctrine {
        ROMAN_DISCIPLINED, GALLIC_ASSAULT, PARTHIAN_SKIRMISH, EGYPTIAN_BALANCED
    }
}
