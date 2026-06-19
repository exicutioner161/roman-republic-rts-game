package units;

import combat.CombatSystem;
import java.awt.Color;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import main.GamePanel;
import pathfinding.Pathfinder;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.TileMap;

public class Unit {
    private static final int[] ALLOWED_UNIT_SIZES = {100, 500, 1000, 2000, 5000};
    private static final double DEFAULT_FACING_ANGLE_RADIANS = -Math.PI * 0.5;
    private static final long RECOVERY_BLOCK_AFTER_DAMAGE_MS = 5000L;
    private static final double HORSE_ARCHER_KITE_RATIO = 0.68;
    private static final double HORSE_ARCHER_RETREAT_DISTANCE = GamePanel.TILE_SIZE * 3.25;
    private static final double HORSE_ARCHER_RETREAT_SPEED_MULTIPLIER = 1.35;
    private static final double CHARIOT_KITE_RATIO = 0.62;
    private static final double CHARIOT_RETREAT_DISTANCE = GamePanel.TILE_SIZE * 2.75;
    private static final double CHARIOT_RETREAT_SPEED_MULTIPLIER = 1.2;
    private static final double HEAVY_INFANTRY_RANGED_JAVELIN_RANGE = GamePanel.TILE_SIZE * 3.75;
    private static final double HEAVY_INFANTRY_MELEE_JAVELIN_RANGE = GamePanel.TILE_SIZE * 1.25;
    private static final double MELEE_CONTACT_EPSILON = GamePanel.TILE_SIZE * 0.25;
    private static final double MELEE_ENGAGEMENT_RANGE = MELEE_CONTACT_EPSILON;
    private static final long PATHFINDING_STALL_TIMEOUT_MS = 3000L;
    private static final double MOVEMENT_PROGRESS_EPSILON = 0.5;
    private static final long ENGAGEMENT_REPATH_INTERVAL_MS = 250L;
    private static final double ENGAGEMENT_REPATH_DISTANCE = GamePanel.TILE_SIZE * 0.35;
    private SoldierClass soldierClass;
    private SoldierCulture soldierCulture;
    private SoldierType soldierType;
    private ExperienceLevel experienceLevel;
    private byte weaponEffectiveness;
    private byte armorEffectiveness;
    private byte mobility;
    private double experiencePoints;
    private int unitSize;
    private int experienceLevelNum;
    private byte morale;
    private byte cohesion;
    private byte stamina;
    // Combat fields
    private double health;
    private double maxHealth;
    private double attackPower;
    private double defense;
    private double attackRange;
    private double rateOfFire; // attacks per second (simple)
    private long lastAttackTimeMs;
    private long lastConditionUpdateMs;
    private long lastDamageTimeMs;
    private DamageEffectType lastDamageEffectType;
    private List<Point> path;
    private final BufferedImage image;
    private boolean selected;
    private boolean colliding;
    private final Point coords;
    private double posX;
    private double posY;
    private Point targetCoords;
    private Point queuedTargetCoords;
    private List<Point> queuedPath;
    private long queuedOrderReadyTimeMs;
    private boolean pathfindingControlled;
    private boolean queuedPathfindingControlled;
    private long lastMovementProgressTimeMs;
    private double lastMovementProgressX;
    private double lastMovementProgressY;
    private Unit engagementTarget;
    private boolean engagementOrdered;
    private boolean engagementTargetLockedByIncomingAttack;
    private long lastEngagementPathUpdateMs;
    private Point lastEngagementTargetCoords;
    private double facingAngleRadians;
    private Double desiredFacingAngleRadians;
    private boolean moving;
    private boolean fighting;
    private boolean supplied;
    private boolean foraging;
    private int carriedFood;
    private boolean surrendered;
    private double speed = 2;
    private double previousPosX;
    private double previousPosY;
    private Color color;
    private boolean movingNorth;
    private boolean movingEast;
    private boolean movingSouth;
    private boolean movingWest;
    private boolean skirmishRetreating;
    private WeaponMode weaponMode;
    private static final Random random = new Random();

    public enum SoldierClass {
        INFANTRY, ARCHER, CAVALRY, HORSE_ARCHER
    }

    public enum SoldierCulture {
        ROMAN, GALLIC, PARTHIAN, EGYPTIAN, BRITON, POMPEIAN
    }

    public enum SoldierType {
        ROMAN_HEAVY_INFANTRY, ROMAN_LIGHT_INFANTRY, ROMAN_SPEARMAN, ROMAN_ARCHER, ROMAN_CAVALRY, GALLIC_INFANTRY,
        GALLIC_CAVALRY, GALLIC_ARCHER, PARTHIAN_INFANTRY, PARTHIAN_CAVALRY, PARTHIAN_ARCHER, PARTHIAN_HORSE_ARCHER,
        EGYPTIAN_INFANTRY, EGYPTIAN_CAVALRY, EGYPTIAN_CHARIOT, EGYPTIAN_ARCHER
    }

    public enum ExperienceLevel {
        GREEN, BASIC, INTERMEDIATE, ADVANCED, VETERAN;
    }

    public enum WeaponMode {
        STANDARD, SWORD, JAVELIN_RANGED, JAVELIN_MELEE
    }

    public enum DamageEffectType {
        NONE, MELEE, RANGED
    }

    public Unit(BufferedImage image, SoldierClass soldierClass, SoldierCulture soldierCulture, SoldierType soldierType,
            int x, int y, int unitSize, int experienceLevelNum) {
        this.image = image;
        colliding = false;
        coords = new Point(x + GamePanel.TILE_SIZE / 2, y + GamePanel.TILE_SIZE / 2);
        this.posX = coords.x;
        this.posY = coords.y;
        initializeMovementTracking();
        this.soldierClass = alignClassWithType(soldierClass, soldierType);
        this.soldierCulture = soldierCulture;
        this.soldierType = soldierType;
        this.unitSize = normalizeUnitSize(unitSize);
        this.experienceLevelNum = experienceLevelNum;
        this.experiencePoints = experienceLevelNum * 100.0;
        this.supplied = true;
        this.foraging = false;
        this.carriedFood = 0;
        this.facingAngleRadians = DEFAULT_FACING_ANGLE_RADIANS;
        this.weaponMode = defaultWeaponModeForType();
        this.color = convCultureToColor();
        assignExperienceLevel();
        assignWeaponEffectiveness();
        assignArmorEffectiveness();
        assignMobility();
        assignMovementSpeed();
        initCombatStats();
    }

    public Unit(BufferedImage image, String soldierClassString, String soldierCultureString, String soldierTypeString,
            int x, int y, int unitSize, int experienceLevelNum) {
        this.image = image;
        colliding = false;
        coords = new Point(x + GamePanel.TILE_SIZE / 2, y + GamePanel.TILE_SIZE / 2);
        this.posX = coords.x;
        this.posY = coords.y;
        initializeMovementTracking();
        SoldierClass parsedClass = convClassStrToEnum(soldierClassString);
        this.soldierCulture = convCultureStrToEnum(soldierCultureString);
        this.soldierType = convSoldierTypeStrToEnum(soldierTypeString);
        this.soldierClass = alignClassWithType(parsedClass, this.soldierType);
        this.unitSize = normalizeUnitSize(unitSize);
        this.experienceLevelNum = experienceLevelNum;
        this.experiencePoints = experienceLevelNum * 100.0;
        this.supplied = true;
        this.foraging = false;
        this.carriedFood = 0;
        this.facingAngleRadians = DEFAULT_FACING_ANGLE_RADIANS;
        this.weaponMode = defaultWeaponModeForType();
        this.color = convCultureToColor();
        assignExperienceLevel();
        assignWeaponEffectiveness();
        assignArmorEffectiveness();
        assignMobility();
        assignMovementSpeed();
        initCombatStats();
    }

    public Unit(BufferedImage image, int x, int y) {
        this.image = image;
        colliding = false;
        coords = new Point(x + GamePanel.TILE_SIZE / 2, y + GamePanel.TILE_SIZE / 2);
        this.posX = coords.x;
        this.posY = coords.y;
        initializeMovementTracking();
        this.soldierClass = SoldierClass.INFANTRY;
        this.soldierCulture = SoldierCulture.ROMAN;
        this.soldierType = SoldierType.ROMAN_HEAVY_INFANTRY;
        this.unitSize = normalizeUnitSize(5000);
        this.experienceLevelNum = 0;
        this.experiencePoints = 0;
        this.supplied = true;
        this.foraging = false;
        this.carriedFood = 0;
        this.facingAngleRadians = DEFAULT_FACING_ANGLE_RADIANS;
        this.weaponMode = defaultWeaponModeForType();
        color = convCultureToColor();
        assignExperienceLevel();
        assignWeaponEffectiveness();
        assignArmorEffectiveness();
        assignMobility();
        assignMovementSpeed();
        initCombatStats();
    }

    private void initCombatStats() {
        // defaults
        this.morale = 100;
        this.cohesion = 100;
        this.stamina = 100;
        // base values influenced by type and unitSize
        // Health scales with formation size so large units do not collapse
        // instantly.
        this.maxHealth = 5 + unitSize * 0.05;
        this.health = this.maxHealth;
        // weapon/armor contribute to attack/defense
        this.attackPower = weaponEffectiveness * 2.0 + experienceLevelNum * 0.5;
        this.defense = armorEffectiveness * 1.5 + experienceLevelNum * 0.3;
        // ranges: archers have longer range
        this.attackRange = switch (soldierType) {
        case PARTHIAN_HORSE_ARCHER -> GamePanel.TILE_SIZE * 6.5;
        case EGYPTIAN_CHARIOT -> GamePanel.TILE_SIZE * 4.5;
        default -> switch (soldierClass) {
        case ARCHER, HORSE_ARCHER -> GamePanel.TILE_SIZE * 6;
        case CAVALRY -> GamePanel.TILE_SIZE * 1.5;
        default -> GamePanel.TILE_SIZE;
        };
        };
        // rate of fire: archers slower
        this.rateOfFire = switch (soldierType) {
        case PARTHIAN_HORSE_ARCHER -> 0.72;
        case EGYPTIAN_CHARIOT -> 0.82;
        default -> switch (soldierClass) {
        case ARCHER, HORSE_ARCHER -> 0.6;
        case CAVALRY -> 0.9;
        default -> 1.0;
        };
        };
        this.lastAttackTimeMs = 0;
        this.lastConditionUpdateMs = System.currentTimeMillis();
        this.lastDamageTimeMs = 0;
        this.lastDamageEffectType = DamageEffectType.NONE;
    }

    private void initializeMovementTracking() {
        pathfindingControlled = false;
        queuedPathfindingControlled = false;
        engagementTarget = null;
        engagementOrdered = false;
        lastEngagementPathUpdateMs = 0L;
        lastEngagementTargetCoords = null;
        resetMovementProgress();
    }

    private void resetMovementProgress() {
        lastMovementProgressTimeMs = System.currentTimeMillis();
        lastMovementProgressX = posX;
        lastMovementProgressY = posY;
    }

    private void clearEngagementTarget() {
        engagementTarget = null;
        engagementOrdered = false;
        engagementTargetLockedByIncomingAttack = false;
        lastEngagementPathUpdateMs = 0L;
        lastEngagementTargetCoords = null;
    }

    private boolean hasEngagementTarget() { return engagementTarget != null && isEnemy(engagementTarget); }

    private Unit resolveCombatTarget(Unit[] others, double range) {
        if (hasEngagementTarget()) {
            if (getCombatDistanceTo(engagementTarget) <= range || engagementTargetLockedByIncomingAttack) {
                return engagementTarget;
            }
            if (!engagementOrdered) {
                clearEngagementTarget();
            } else {
                return null;
            }
        } else if (engagementOrdered) {
            clearEngagementTarget();
            return null;
        }
        Unit acquiredTarget = findNearestEnemyWithinRange(others, range);
        if (acquiredTarget != null) {
            engagementTarget = acquiredTarget;
            engagementOrdered = false;
        }
        return acquiredTarget;
    }

    private void lockIncomingAttacker(Unit attacker) {
        if (attacker == null || attacker == this || !isEnemy(attacker)) {
            return;
        }
        if (engagementOrdered || engagementTargetLockedByIncomingAttack) {
            return;
        }
        engagementTarget = attacker;
        engagementTargetLockedByIncomingAttack = true;
        faceUnit(attacker);
    }

    private void updateEngagementOrder() {
        if (!engagementOrdered) {
            if (!hasEngagementTarget()) {
                clearEngagementTarget();
            }
            return;
        }
        if (!hasEngagementTarget()) {
            clearEngagementTarget();
            return;
        }
        faceUnit(engagementTarget);
        double requiredRange = usesProjectileAttack() ? getAttackRange() : MELEE_ENGAGEMENT_RANGE;
        if (getCombatDistanceTo(engagementTarget) <= requiredRange) {
            if (moving) {
                clearActiveMovementOrder();
            }
            return;
        }
        pursueEngagementTarget();
    }

    private void pursueEngagementTarget() {
        TileMap activeMap = UnitManager.getActiveMap();
        Point desiredTargetCoords = new Point(engagementTarget.getCoords());
        if (!shouldRefreshEngagementPath(desiredTargetCoords)) {
            return;
        }
        if (issuePathOrder(desiredTargetCoords, activeMap, false)) {
            lastEngagementTargetCoords = desiredTargetCoords;
            lastEngagementPathUpdateMs = System.currentTimeMillis();
        }
    }

    private boolean shouldRefreshEngagementPath(Point desiredTargetCoords) {
        if (desiredTargetCoords == null) {
            return false;
        }
        if (lastEngagementTargetCoords == null) {
            return true;
        }
        if (lastEngagementTargetCoords.distance(desiredTargetCoords) > ENGAGEMENT_REPATH_DISTANCE) {
            return true;
        }
        return moving && targetCoords != null && targetCoords.distance(desiredTargetCoords) > ENGAGEMENT_REPATH_DISTANCE
                && System.currentTimeMillis() - lastEngagementPathUpdateMs >= ENGAGEMENT_REPATH_INTERVAL_MS;
    }

    private boolean issuePathOrder(Point coord, TileMap map, boolean clearEngagementTarget) {
        if (coord == null) {
            return false;
        }
        if (clearEngagementTarget) {
            clearEngagementTarget();
        }
        foraging = false;
        if (map == null) {
            queueOrder(coord, null);
            return true;
        }
        Point clampedTarget = clampPointToWorldBounds(coord, map);
        int currentCol = Math.clamp(getX() / GamePanel.TILE_SIZE, 0, map.getWidth() - 1);
        int currentRow = Math.clamp(getY() / GamePanel.TILE_SIZE, 0, map.getHeight() - 1);
        int goalCol = Math.clamp(clampedTarget.x / GamePanel.TILE_SIZE, 0, map.getWidth() - 1);
        int goalRow = Math.clamp(clampedTarget.y / GamePanel.TILE_SIZE, 0, map.getHeight() - 1);
        if (currentCol == goalCol && currentRow == goalRow) {
            queueOrder(clampedTarget, null);
            return true;
        }
        List<Point> plannedPath = Pathfinder.findPath(map, getCoords(), clampedTarget, GamePanel.TILE_SIZE, true, this);
        if (plannedPath.isEmpty()) {
            plannedPath = Pathfinder.findPath(map, getCoords(), clampedTarget, GamePanel.TILE_SIZE, true);
        }
        if (plannedPath.isEmpty()) {
            return false;
        }
        queueOrder(clampedTarget, plannedPath);
        return true;
    }

    public double getAttackPower() {
        if (isRomanHeavyInfantryRangedJavelinMode()) {
            return attackPower * 0.82;
        }
        if (isRomanHeavyInfantryMeleeJavelinMode()) {
            return attackPower * 1.04;
        }
        return attackPower;
    }

    public double getDefense() {
        if (isRomanHeavyInfantryRangedJavelinMode()) {
            return defense * 0.92;
        }
        if (isRomanHeavyInfantryMeleeJavelinMode()) {
            return defense * 1.08;
        }
        return defense;
    }

    public double getHealth() { return health; }

    public double getMaxHealth() { return maxHealth; }

    public double getAttackRange() {
        double baseRange;
        if (isRomanHeavyInfantryRangedJavelinMode()) {
            baseRange = HEAVY_INFANTRY_RANGED_JAVELIN_RANGE;
        } else if (isRomanHeavyInfantryMeleeJavelinMode()) {
            baseRange = HEAVY_INFANTRY_MELEE_JAVELIN_RANGE;
        } else {
            baseRange = attackRange;
        }
        TileMap activeTileMap = UnitManager.getActiveMap();
        if (activeTileMap != null && usesProjectileAttack()) {
            int heightLevel = activeTileMap.getHeightLevelAtWorld(posX, posY);
            if (heightLevel > 0) {
                baseRange *= 1.0 + heightLevel * 0.12;
            }
        }
        return baseRange;
    }

    public double getRateOfFire() {
        if (isRomanHeavyInfantryRangedJavelinMode()) {
            return 0.6;
        }
        if (isRomanHeavyInfantryMeleeJavelinMode()) {
            return 0.95;
        }
        return rateOfFire;
    }

    private WeaponMode defaultWeaponModeForType() {
        return soldierType == SoldierType.ROMAN_HEAVY_INFANTRY ? WeaponMode.SWORD : WeaponMode.STANDARD;
    }

    private static int normalizeUnitSize(int requestedSize) {
        int nearest = ALLOWED_UNIT_SIZES[0];
        int smallestDelta = Math.abs(requestedSize - nearest);
        for (int allowedSize : ALLOWED_UNIT_SIZES) {
            int delta = Math.abs(requestedSize - allowedSize);
            if (delta < smallestDelta) {
                nearest = allowedSize;
                smallestDelta = delta;
            }
        }
        return nearest;
    }

    private SoldierClass alignClassWithType(SoldierClass desiredClass, SoldierType type) {
        return switch (type) {
        case ROMAN_ARCHER, GALLIC_ARCHER, PARTHIAN_ARCHER, EGYPTIAN_ARCHER -> SoldierClass.ARCHER;
        case PARTHIAN_HORSE_ARCHER -> SoldierClass.HORSE_ARCHER;
        case ROMAN_CAVALRY, GALLIC_CAVALRY, PARTHIAN_CAVALRY, EGYPTIAN_CAVALRY, EGYPTIAN_CHARIOT -> SoldierClass.CAVALRY;
        default -> desiredClass != null ? desiredClass : SoldierClass.INFANTRY;
        };
    }

    private SoldierClass convClassStrToEnum(String soldierClassString) {
        return switch (soldierClassString.toUpperCase()) {
        case "INFANTRY" -> SoldierClass.INFANTRY;
        case "ARCHER" -> SoldierClass.ARCHER;
        case "CAVALRY" -> SoldierClass.CAVALRY;
        case "HORSE_ARCHER" -> SoldierClass.HORSE_ARCHER;
        default -> SoldierClass.INFANTRY;
        };
    }

    private SoldierCulture convCultureStrToEnum(String soldierCultureString) {
        return switch (soldierCultureString.toUpperCase()) {
        case "ROMAN" -> SoldierCulture.ROMAN;
        case "GALLIC" -> SoldierCulture.GALLIC;
        case "PARTHIAN" -> SoldierCulture.PARTHIAN;
        case "EGYPTIAN" -> SoldierCulture.EGYPTIAN;
        case "BRITON" -> SoldierCulture.BRITON;
        case "POMPEIAN" -> SoldierCulture.POMPEIAN;
        default -> SoldierCulture.ROMAN;
        };
    }

    private Color convCultureToColor() {
        return switch (soldierCulture) {
        case ROMAN -> new Color(150, 0, 0);
        case EGYPTIAN -> Color.YELLOW;
        case GALLIC -> new Color(0, 100, 0);
        case PARTHIAN -> Color.BLUE;
        case BRITON -> new Color(96, 72, 24);
        case POMPEIAN -> new Color(92, 92, 132);
        default -> Color.BLACK;
        };
    }

    private SoldierType convSoldierTypeStrToEnum(String soldierTypeString) {
        return switch (soldierTypeString.toUpperCase()) {
        case "ROMAN_HEAVY_INFANTRY" -> SoldierType.ROMAN_HEAVY_INFANTRY;
        case "ROMAN_LIGHT_INFANTRY" -> SoldierType.ROMAN_LIGHT_INFANTRY;
        case "ROMAN_SPEARMAN" -> SoldierType.ROMAN_SPEARMAN;
        case "ROMAN_ARCHER" -> SoldierType.ROMAN_ARCHER;
        case "ROMAN_CAVALRY" -> SoldierType.ROMAN_CAVALRY;
        case "GALLIC_INFANTRY" -> SoldierType.GALLIC_INFANTRY;
        case "GALLIC_CAVALRY" -> SoldierType.GALLIC_CAVALRY;
        case "GALLIC_ARCHER" -> SoldierType.GALLIC_ARCHER;
        case "PARTHIAN_INFANTRY" -> SoldierType.PARTHIAN_INFANTRY;
        case "PARTHIAN_CAVALRY" -> SoldierType.PARTHIAN_CAVALRY;
        case "PARTHIAN_ARCHER" -> SoldierType.PARTHIAN_ARCHER;
        case "PARTHIAN_HORSE_ARCHER" -> SoldierType.PARTHIAN_HORSE_ARCHER;
        case "EGYPTIAN_INFANTRY" -> SoldierType.EGYPTIAN_INFANTRY;
        case "EGYPTIAN_CAVALRY" -> SoldierType.EGYPTIAN_CAVALRY;
        case "EGYPTIAN_CHARIOT" -> SoldierType.EGYPTIAN_CHARIOT;
        case "EGYPTIAN_ARCHER" -> SoldierType.EGYPTIAN_ARCHER;
        default -> SoldierType.ROMAN_HEAVY_INFANTRY;
        };
    }

    private void assignExperienceLevel() {
        switch (experienceLevelNum) {
        case 0, 1 -> experienceLevel = ExperienceLevel.GREEN;
        case 2, 3 -> experienceLevel = ExperienceLevel.BASIC;
        case 4, 5, 6 -> experienceLevel = ExperienceLevel.INTERMEDIATE;
        case 7, 8 -> experienceLevel = ExperienceLevel.ADVANCED;
        case 9, 10 -> experienceLevel = ExperienceLevel.VETERAN;
        default -> experienceLevel = ExperienceLevel.VETERAN;
        }
    }

    private void assignWeaponEffectiveness() {
        weaponEffectiveness = switch (soldierType) {
        case ROMAN_HEAVY_INFANTRY -> 5;
        case ROMAN_LIGHT_INFANTRY -> 3;
        case ROMAN_SPEARMAN -> 4;
        case ROMAN_ARCHER -> 3;
        case ROMAN_CAVALRY -> 4;
        case GALLIC_INFANTRY -> 4;
        case GALLIC_CAVALRY -> 3;
        case GALLIC_ARCHER -> 2;
        case PARTHIAN_INFANTRY -> 2;
        case PARTHIAN_CAVALRY -> 5;
        case PARTHIAN_ARCHER -> 4;
        case PARTHIAN_HORSE_ARCHER -> 5;
        case EGYPTIAN_INFANTRY -> 3;
        case EGYPTIAN_CAVALRY -> 4;
        case EGYPTIAN_CHARIOT -> 5;
        case EGYPTIAN_ARCHER -> 2;
        default -> 1;
        };
    }

    private void assignArmorEffectiveness() {
        armorEffectiveness = switch (soldierType) {
        case ROMAN_HEAVY_INFANTRY -> 5;
        case ROMAN_LIGHT_INFANTRY -> 2;
        case ROMAN_SPEARMAN -> 4;
        case ROMAN_ARCHER -> 2;
        case ROMAN_CAVALRY -> 5;
        case GALLIC_INFANTRY -> 2;
        case GALLIC_CAVALRY -> 3;
        case GALLIC_ARCHER -> 1;
        case PARTHIAN_INFANTRY -> 3;
        case PARTHIAN_CAVALRY -> 4;
        case PARTHIAN_ARCHER -> 1;
        case PARTHIAN_HORSE_ARCHER -> 4;
        case EGYPTIAN_INFANTRY -> 4;
        case EGYPTIAN_CAVALRY -> 4;
        case EGYPTIAN_CHARIOT -> 3;
        case EGYPTIAN_ARCHER -> 2;
        default -> 1;
        };
    }

    private void assignMobility() {
        switch (soldierType) {
        case ROMAN_HEAVY_INFANTRY -> mobility = 2;
        case ROMAN_LIGHT_INFANTRY -> mobility = 4;
        case ROMAN_SPEARMAN -> mobility = 3;
        case ROMAN_ARCHER -> mobility = 3;
        case ROMAN_CAVALRY -> mobility = 4;
        case GALLIC_INFANTRY -> mobility = 4;
        case GALLIC_CAVALRY -> mobility = 5;
        case GALLIC_ARCHER -> mobility = 5;
        case PARTHIAN_INFANTRY -> mobility = 2;
        case PARTHIAN_CAVALRY -> mobility = 4;
        case PARTHIAN_ARCHER -> mobility = 3;
        case PARTHIAN_HORSE_ARCHER -> mobility = 5;
        case EGYPTIAN_INFANTRY -> mobility = 2;
        case EGYPTIAN_CAVALRY -> mobility = 5;
        case EGYPTIAN_CHARIOT -> mobility = 4;
        case EGYPTIAN_ARCHER -> mobility = 3;
        default -> mobility = 2;
        }
    }

    private void assignMovementSpeed() {
        speed = switch (soldierType) {
        case ROMAN_HEAVY_INFANTRY, PARTHIAN_INFANTRY, EGYPTIAN_INFANTRY -> 1.18;
        case ROMAN_SPEARMAN -> 1.26;
        case ROMAN_LIGHT_INFANTRY, GALLIC_INFANTRY -> 1.34;
        case ROMAN_ARCHER, GALLIC_ARCHER, PARTHIAN_ARCHER, EGYPTIAN_ARCHER -> 1.24;
        case ROMAN_CAVALRY, PARTHIAN_CAVALRY, EGYPTIAN_CAVALRY -> 2.18;
        case GALLIC_CAVALRY -> 2.24;
        case PARTHIAN_HORSE_ARCHER -> 2.36;
        case EGYPTIAN_CHARIOT -> 2.02;
        };
    }

    public void transferSoldiers(Unit destinationUnit, int numSoldiers) {
        this.unitSize -= numSoldiers;
        destinationUnit.increaseUnitSize(numSoldiers);
    }

    public void update(Unit[] allUnits) {
        fighting = false;
        processQueuedOrder();
        if (surrendered) {
            moving = false;
            selected = false;
            return;
        }
        updateEngagementOrder();
        engageRangedTargets(allUnits);
        if (moving) {
            rememberPositionBeforeMove();
            move();
            handleCollisions(allUnits);
            stopPathfindingIfStalled();
            if (moving && reachedTarget()) {
                moving = false;
                targetCoords = null;
                pathfindingControlled = false;
            }
        } else {
            handleCollisions(allUnits);
        }
        engageCloseCombatTargets(allUnits);
        updateCombatState();
    }

    private void stopPathfindingIfStalled() {
        if (!moving) {
            return;
        }
        if (hasMovementProgress()) {
            resetMovementProgress();
            return;
        }
        if (pathfindingControlled
                && System.currentTimeMillis() - lastMovementProgressTimeMs >= PATHFINDING_STALL_TIMEOUT_MS) {
            clearActiveMovementOrder();
        }
    }

    private boolean hasMovementProgress() {
        return Math.hypot(posX - lastMovementProgressX, posY - lastMovementProgressY) > MOVEMENT_PROGRESS_EPSILON;
    }

    private void engageRangedTargets(Unit[] allUnits) {
        if (!usesProjectileAttack()) {
            return;
        }
        Unit focusEnemy = hasEngagementTarget() ? engagementTarget : findNearestEnemy(allUnits);
        if (isKitingSkirmisher() && focusEnemy != null && !engagementOrdered) {
            attemptSkirmisherKite(focusEnemy);
        }
        Unit target = resolveCombatTarget(allUnits, getAttackRange());
        if (target == null) {
            return;
        }
        updateFacingFromVector(target.getX() - posX, target.getY() - posY);
        attack(target);
    }

    private void engageCloseCombatTargets(Unit[] allUnits) {
        // if (usesProjectileAttack()) {
        // return;
        // }
        Unit target = resolveCombatTarget(allUnits, getAttackRange());
        if (target == null) {
            return;
        }
        faceUnit(target);
        if (getCombatDistanceTo(target) > MELEE_ENGAGEMENT_RANGE) {
            return;
        }
        if (moving) {
            stopMovementOnCollision();
        }
        attack(target);
    }

    private void attemptSkirmisherKite(Unit nearestEnemy) {
        if (nearestEnemy == null) {
            return;
        }
        double distance = getCombatDistanceTo(nearestEnemy);
        if (distance > getAttackRange() * getSkirmishRetreatRatio()) {
            skirmishRetreating = false;
            return;
        }
        double awayX = posX - nearestEnemy.getX();
        double awayY = posY - nearestEnemy.getY();
        double length = Math.hypot(awayX, awayY);
        if (length < 1e-6) {
            awayX = -getFacingVectorX();
            awayY = -getFacingVectorY();
            length = Math.hypot(awayX, awayY);
        }
        if (length < 1e-6) {
            awayX = 1.0;
            awayY = 0.0;
            length = 1.0;
        }
        double retreatX = posX + awayX / length * getSkirmishRetreatDistance();
        double retreatY = posY + awayY / length * getSkirmishRetreatDistance();
        Point retreatPoint = clampPointToWorldBounds(new Point((int)Math.round(retreatX), (int)Math.round(retreatY)),
                UnitManager.getActiveMap());
        double retreatGap = Math.hypot(retreatPoint.x - posX, retreatPoint.y - posY);
        if (retreatGap < speed) {
            skirmishRetreating = false;
            return;
        }
        targetCoords = retreatPoint;
        path = null;
        queuedTargetCoords = null;
        queuedPath = null;
        clearEngagementTarget();
        pathfindingControlled = false;
        queuedPathfindingControlled = false;
        skirmishRetreating = true;
        moving = true;
        resetMovementProgress();
    }

    private Unit findNearestEnemy(Unit[] others) {
        Unit nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Unit other : others) {
            double distance = getCombatDistanceTo(other);
            if (!isEnemy(other) || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            nearest = other;
        }
        return nearest;
    }

    private Unit findNearestEnemyWithinRange(Unit[] others, double range) {
        Unit nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Unit other : others) {
            double distance = getCombatDistanceTo(other);
            if (!isEnemy(other) || distance > range || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            nearest = other;
        }
        return nearest;
    }

    private boolean isEnemy(Unit other) {
        return other != null && other != this && !other.hasSurrendered() && other.getHealth() > 0
                && other.getSoldierCulture() != soldierCulture;
    }

    private boolean usesProjectileAttack() {
        return soldierClass == SoldierClass.ARCHER || soldierClass == SoldierClass.HORSE_ARCHER
                || soldierType == SoldierType.EGYPTIAN_CHARIOT || isRomanHeavyInfantryRangedJavelinMode();
    }

    private boolean isKitingSkirmisher() {
        return soldierClass == SoldierClass.HORSE_ARCHER || soldierType == SoldierType.EGYPTIAN_CHARIOT;
    }

    private double getSkirmishRetreatRatio() {
        return soldierType == SoldierType.EGYPTIAN_CHARIOT ? CHARIOT_KITE_RATIO : HORSE_ARCHER_KITE_RATIO;
    }

    private double getSkirmishRetreatDistance() {
        return soldierType == SoldierType.EGYPTIAN_CHARIOT ? CHARIOT_RETREAT_DISTANCE : HORSE_ARCHER_RETREAT_DISTANCE;
    }

    private double getCurrentMoveSpeed(Point waypoint) {
        double adjustedSpeed = getTerrainAdjustedMoveSpeed(waypoint);
        if (!skirmishRetreating) {
            return adjustedSpeed;
        }
        return soldierType == SoldierType.EGYPTIAN_CHARIOT ? adjustedSpeed * CHARIOT_RETREAT_SPEED_MULTIPLIER
                : adjustedSpeed * HORSE_ARCHER_RETREAT_SPEED_MULTIPLIER;
    }

    private double getTerrainAdjustedMoveSpeed(Point waypoint) {
        TileMap activeTileMap = UnitManager.getActiveMap();
        if (activeTileMap == null) {
            return speed;
        }
        TerrainType currentTerrain = activeTileMap.getTerrainTypeAtWorld(posX, posY);
        TerrainType nextTerrain = waypoint != null ? activeTileMap.getTerrainTypeAtWorld(waypoint.x, waypoint.y)
                : currentTerrain;
        double terrainFactor = 0.5 * (getTerrainMovementFactor(currentTerrain) + getTerrainMovementFactor(nextTerrain));
        int heightDelta = nextTerrain.getHeightLevel() - currentTerrain.getHeightLevel();
        if (heightDelta > 0) {
            terrainFactor *= Math.max(0.52, 1.0 - heightDelta * 0.14);
        } else if (heightDelta < 0) {
            terrainFactor *= 1.0 + (-heightDelta) * 0.06;
        }
        return Math.max(0.32, speed * terrainFactor);
    }

    private double getTerrainMovementFactor(TerrainType terrainType) {
        if (terrainType == null) {
            return 1.0;
        }
        return switch (soldierClass) {
        case CAVALRY -> switch (terrainType) {
        case FLAT_GRASS -> 1.18;
        case HILLY_GRASS -> 0.82;
        case IMPASSABLE_MOUNTAIN -> 0.38;
        case MOUNTAIN_PASS -> 0.66;
        case RIVER -> 0.56;
        case FLAT_FOREST -> 0.72;
        case HILLY_FOREST -> 0.62;
        case MOUNTAINOUS_FOREST -> 0.52;
        case MARSHLAND -> 0.44;
        case FORESTED_MARSHLAND -> 0.38;
        case FLAT_MUD -> 0.74;
        case FORESTED_MUD -> 0.58;
        case HILLY_MUD -> 0.62;
        };
        case HORSE_ARCHER -> switch (terrainType) {
        case FLAT_GRASS -> 1.12;
        case HILLY_GRASS -> 0.88;
        case IMPASSABLE_MOUNTAIN -> 0.42;
        case MOUNTAIN_PASS -> 0.74;
        case RIVER -> 0.62;
        case FLAT_FOREST -> 0.76;
        case HILLY_FOREST -> 0.68;
        case MOUNTAINOUS_FOREST -> 0.58;
        case MARSHLAND -> 0.50;
        case FORESTED_MARSHLAND -> 0.44;
        case FLAT_MUD -> 0.80;
        case FORESTED_MUD -> 0.64;
        case HILLY_MUD -> 0.68;
        };
        case ARCHER -> switch (terrainType) {
        case FLAT_GRASS -> 1.0;
        case HILLY_GRASS -> 0.92;
        case IMPASSABLE_MOUNTAIN -> 0.44;
        case MOUNTAIN_PASS -> 0.78;
        case RIVER -> 0.68;
        case FLAT_FOREST -> 0.84;
        case HILLY_FOREST -> 0.76;
        case MOUNTAINOUS_FOREST -> 0.66;
        case MARSHLAND -> 0.60;
        case FORESTED_MARSHLAND -> 0.54;
        case FLAT_MUD -> 0.78;
        case FORESTED_MUD -> 0.68;
        case HILLY_MUD -> 0.72;
        };
        case INFANTRY -> switch (terrainType) {
        case FLAT_GRASS -> 1.0;
        case HILLY_GRASS -> 0.94;
        case IMPASSABLE_MOUNTAIN -> 0.46;
        case MOUNTAIN_PASS -> 0.82;
        case RIVER -> 0.72;
        case FLAT_FOREST -> 0.88;
        case HILLY_FOREST -> 0.80;
        case MOUNTAINOUS_FOREST -> 0.70;
        case MARSHLAND -> 0.64;
        case FORESTED_MARSHLAND -> 0.58;
        case FLAT_MUD -> 0.82;
        case FORESTED_MUD -> 0.72;
        case HILLY_MUD -> 0.76;
        };
        };
    }

    private double getCombatDistanceTo(Unit other) { return Math.max(0.0, getSignedFootprintGapTo(other)); }

    public double getFootprintGapTo(Unit other) { return Math.max(0.0, getSignedFootprintGapTo(other)); }

    public boolean isTouching(Unit other) { return getFootprintGapTo(other) <= MELEE_CONTACT_EPSILON; }

    private double getSignedFootprintGapTo(Unit other) { return getSignedFootprintGapAt(posX, posY, other); }

    private double getSignedFootprintGapAt(double centerX, double centerY, Unit other) {
        if (other == null || other == this) {
            return Double.MAX_VALUE;
        }
        double dx = other.posX - centerX;
        double dy = other.posY - centerY;
        double centerDistance = Math.hypot(dx, dy);
        if (centerDistance < 1e-6) {
            return -Math.max(getFormationWidth(), getFormationHeight());
        }
        double nx = dx / centerDistance;
        double ny = dy / centerDistance;
        double requiredCenterDistance = getSupportRadiusAlong(nx, ny) + other.getSupportRadiusAlong(-nx, -ny);
        return centerDistance - requiredCenterDistance;
    }

    private double getSupportRadiusAlong(double dirX, double dirY) {
        double directionLength = Math.hypot(dirX, dirY);
        if (directionLength < 1e-6) {
            return Math.max(getFormationWidth(), getFormationHeight()) * 0.5;
        }
        double nx = dirX / directionLength;
        double ny = dirY / directionLength;
        double renderAngle = getRenderAngleRadians();
        double widthAxisX = Math.cos(renderAngle);
        double widthAxisY = Math.sin(renderAngle);
        double heightAxisX = -Math.sin(renderAngle);
        double heightAxisY = Math.cos(renderAngle);
        double halfWidth = getFormationWidth() * 0.5;
        double halfHeight = getFormationHeight() * 0.5;
        return Math.abs(nx * widthAxisX + ny * widthAxisY) * halfWidth
                + Math.abs(nx * heightAxisX + ny * heightAxisY) * halfHeight;
    }

    public void move() {
        if (surrendered || cohesion <= 0) {
            moving = false;
            skirmishRetreating = false;
            return;
        }
        Point waypoint = getCurrentWaypoint();
        if (waypoint == null) {
            skirmishRetreating = false;
            return;
        }
        double dx = waypoint.x - posX;
        double dy = waypoint.y - posY;
        double dist = Math.hypot(dx, dy);
        if (dist < 1e-6) {
            finishWaypoint(waypoint);
            return;
        }
        advanceTowardsWaypoint(dx, dy, dist);
        if (Math.hypot(waypoint.x - posX, waypoint.y - posY) <= 0.001) {
            finishWaypoint(waypoint);
        }
    }

    private Point getCurrentWaypoint() {
        if (path != null && !path.isEmpty()) {
            return path.get(0);
        }
        return targetCoords;
    }

    private void advanceTowardsWaypoint(double dx, double dy, double dist) {
        double nx = dx / dist;
        double ny = dy / dist;
        double step = Math.min(getCurrentMoveSpeed(getCurrentWaypoint()), dist);
        double moveX = nx * step;
        double moveY = ny * step;
        updateFacingFromVector(dx, dy);
        applyMovementStep(moveX, moveY);
    }

    private void applyMovementStep(double moveX, double moveY) {
        posX += moveX;
        posY += moveY;
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
        clampToWorldBounds(UnitManager.getActiveMap());
        movingNorth = moveY < 0;
        movingSouth = moveY > 0;
        movingWest = moveX < 0;
        movingEast = moveX > 0;
    }

    private void finishWaypoint(Point waypoint) {
        posX = waypoint.x;
        posY = waypoint.y;
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
        clampToWorldBounds(UnitManager.getActiveMap());
        if (path != null && !path.isEmpty()) {
            path.remove(0);
            if (path.isEmpty() && targetCoords != null && coords.distance(targetCoords) <= speed) {
                targetCoords = null;
                moving = false;
                skirmishRetreating = false;
                pathfindingControlled = false;
                applyDesiredFacing();
            }
        } else {
            targetCoords = null;
            moving = false;
            skirmishRetreating = false;
            pathfindingControlled = false;
            applyDesiredFacing();
        }
    }

    private void applyDesiredFacing() {
        if (desiredFacingAngleRadians == null) {
            return;
        }
        facingAngleRadians = desiredFacingAngleRadians;
        desiredFacingAngleRadians = null;
    }

    public boolean isMoving() { return moving; }

    public void setMoving(boolean moving) { this.moving = moving; }

    public boolean reachedTarget() {
        return targetCoords != null && Math.hypot(targetCoords.x - posX, targetCoords.y - posY) <= speed;
    }

    private Rectangle2D.Double createBoundsAt(double centerX, double centerY) {
        double width = getFormationWidth();
        double height = getFormationHeight();
        return new Rectangle2D.Double(centerX - width * 0.5, centerY - height * 0.5, width, height);
    }

    public Rectangle2D.Double getBounds() { return createBoundsAt(posX, posY); }

    public Rectangle2D getFootprintBounds() { return getFootprintShape().getBounds2D(); }

    public Shape getFootprintShape() { return getFootprintShapeAt(posX, posY); }

    public Shape getFootprintShapeAt(Point center) {
        if (center == null) {
            return new Rectangle2D.Double();
        }
        return getFootprintShapeAt(center.getX(), center.getY());
    }

    private Shape getFootprintShapeAt(double centerX, double centerY) {
        AffineTransform transform = AffineTransform.getRotateInstance(getRenderAngleRadians(), centerX, centerY);
        return transform.createTransformedShape(createBoundsAt(centerX, centerY));
    }

    public double getRenderAngleRadians() { return facingAngleRadians + Math.PI * 0.5; }

    public double getFormationWidth() {
        double sizeFactor = switch (unitSize) {
        case 100 -> 0.72;
        case 500 -> 0.86;
        case 1000 -> 0.98;
        case 2000 -> 1.10;
        case 5000 -> 1.24;
        default -> 0.86;
        };
        double classFactor = switch (soldierClass) {
        case INFANTRY -> 1.12;
        case ARCHER -> 1.00;
        case CAVALRY -> 1.24;
        case HORSE_ARCHER -> 1.32;
        };
        return GamePanel.TILE_SIZE * sizeFactor * classFactor;
    }

    public double getFormationHeight() {
        double sizeFactor = switch (unitSize) {
        case 100 -> 0.60;
        case 500 -> 0.70;
        case 1000 -> 0.80;
        case 2000 -> 0.90;
        case 5000 -> 0.98;
        default -> 0.70;
        };
        double classFactor = switch (soldierClass) {
        case INFANTRY -> 0.94;
        case ARCHER -> 0.86;
        case CAVALRY -> 0.88;
        case HORSE_ARCHER -> 0.84;
        };
        return GamePanel.TILE_SIZE * sizeFactor * classFactor;
    }

    public boolean intersects(Rectangle2D rect) {
        if (rect == null) {
            return false;
        }
        Area intersection = new Area(getFootprintShape());
        intersection.intersect(new Area(rect));
        return !intersection.isEmpty();
    }

    public boolean intersects(Unit other) {
        return other != null && other != this && getSignedFootprintGapTo(other) < 0.0;
    }

    public boolean intersectsAt(Point center, Unit other) {
        if (center == null || other == null || other == this) {
            return false;
        }
        return getSignedFootprintGapAt(center.getX(), center.getY(), other) < 0.0;
    }

    public void handleCollisions(Unit[] others) {
        colliding = false;
        for (Unit other : others) {
            if (other != this && intersects(other)) {
                colliding = true;
                resolveCollision(other);
            }
        }
    }

    private void resolveCollision(Unit other) {
        boolean separated = separateFrom(other);
        if (!separated && moving) {
            revertToPreviousPosition();
            separated = separateFrom(other);
        }
        if (other.getSoldierCulture() != soldierCulture) {
            faceUnit(other);
            other.faceUnit(this);
            stopMovementOnCollision();
            return;
        }
        if (moving && hasActiveMovementOrder() && attemptFriendlyCollisionReroute()) {
            return;
        }
        if (!separated && intersects(other)) {
            stopMovementOnCollision();
        }
    }

    private void rememberPositionBeforeMove() {
        previousPosX = posX;
        previousPosY = posY;
    }

    private void revertToPreviousPosition() {
        if (!moving) {
            return;
        }
        posX = previousPosX;
        posY = previousPosY;
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
        clampToWorldBounds(UnitManager.getActiveMap());
    }

    private boolean separateFrom(Unit other) {
        if (other == null) {
            return true;
        }
        double penetration = -getSignedFootprintGapTo(other);
        if (penetration <= 0.0) {
            return true;
        }
        double pushX = posX - other.posX;
        double pushY = posY - other.posY;
        double length = Math.hypot(pushX, pushY);
        if (length < 1e-6) {
            pushX = posX - previousPosX;
            pushY = posY - previousPosY;
            length = Math.hypot(pushX, pushY);
        }
        if (length < 1e-6) {
            pushX = -getFacingVectorX();
            pushY = -getFacingVectorY();
            length = Math.hypot(pushX, pushY);
        }
        if (length < 1e-6) {
            pushX = 0.0;
            pushY = 1.0;
            length = 1.0;
        }
        double originalPosX = posX;
        double originalPosY = posY;
        posX += pushX / length * penetration;
        posY += pushY / length * penetration;
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
        clampToWorldBounds(UnitManager.getActiveMap());
        if (!intersects(other)) {
            return true;
        }
        posX = originalPosX;
        posY = originalPosY;
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
        clampToWorldBounds(UnitManager.getActiveMap());
        return false;
    }

    private boolean attemptFriendlyCollisionReroute() {
        TileMap activeMap = UnitManager.getActiveMap();
        if (activeMap == null || targetCoords == null) {
            return false;
        }
        List<Point> reroutedPath = Pathfinder.findPath(activeMap, getCoords(), targetCoords, GamePanel.TILE_SIZE, true,
                this);
        if (reroutedPath.isEmpty()) {
            reroutedPath = Pathfinder.findPath(activeMap, getCoords(), targetCoords, GamePanel.TILE_SIZE, true);
        }
        if (reroutedPath.isEmpty()) {
            return false;
        }
        path = reroutedPath;
        moving = true;
        pathfindingControlled = true;
        skirmishRetreating = false;
        resetMovementProgress();
        return true;
    }

    private boolean hasActiveMovementOrder() { return targetCoords != null || (path != null && !path.isEmpty()); }

    private void stopMovementOnCollision() {
        if (!moving && targetCoords == null && (path == null || path.isEmpty())) {
            return;
        }
        clearActiveMovementOrder();
    }

    private void clearActiveMovementOrder() {
        moving = false;
        skirmishRetreating = false;
        targetCoords = null;
        path = null;
        pathfindingControlled = false;
    }

    private void clampToWorldBounds(TileMap map) {
        if (map == null) {
            return;
        }
        double halfWidth = getFormationWidth() * 0.5;
        double halfHeight = getFormationHeight() * 0.5;
        double minX = halfWidth;
        double minY = halfHeight;
        double maxX = map.getWidth() * GamePanel.TILE_SIZE - halfWidth;
        double maxY = map.getHeight() * GamePanel.TILE_SIZE - halfHeight;
        posX = Math.clamp(posX, minX, Math.max(minX, maxX));
        posY = Math.clamp(posY, minY, Math.max(minY, maxY));
        coords.setLocation((int)Math.round(posX), (int)Math.round(posY));
    }

    private Point clampPointToWorldBounds(Point point, TileMap map) {
        if (point == null || map == null) {
            return point;
        }
        double halfWidth = getFormationWidth() * 0.5;
        double halfHeight = getFormationHeight() * 0.5;
        int minX = (int)Math.round(halfWidth);
        int minY = (int)Math.round(halfHeight);
        int maxX = (int)Math.round(map.getWidth() * GamePanel.TILE_SIZE - halfWidth);
        int maxY = (int)Math.round(map.getHeight() * GamePanel.TILE_SIZE - halfHeight);
        return new Point(Math.clamp(point.x, minX, Math.max(minX, maxX)),
                Math.clamp(point.y, minY, Math.max(minY, maxY)));
    }

    // Combat
    public void attack(Unit target) {
        if (target == null) {
            return;
        }
        fighting = true;
        double dist = getCombatDistanceTo(target);
        if (dist <= getAttackRange()) {
            long now = System.currentTimeMillis();
            if (now - lastAttackTimeMs >= (long)(1000.0 / Math.max(0.0001, getRateOfFire()))) {
                lastAttackTimeMs = now;
                double dmg = CombatSystem.calculateDamage(this, target);
                if (usesProjectileAttack() && (!isRomanHeavyInfantry() || dist > GamePanel.TILE_SIZE * 1.25)) {
                    UnitManager.spawnProjectile(this, target, dmg);
                } else {
                    target.receiveDamage(dmg, this, DamageEffectType.MELEE);
                    gainExperience(dmg * 0.1);
                }
            }
        }
    }

    public void receiveDamage(double amount) { receiveDamage(amount, null, DamageEffectType.MELEE); }

    public void receiveDamage(double amount, Unit attacker) { receiveDamage(amount, attacker, DamageEffectType.MELEE); }

    public synchronized void receiveDamage(double amount, Unit attacker, DamageEffectType damageEffectType) {
        if (amount <= 0) {
            return;
        }
        lockIncomingAttacker(attacker);
        this.lastDamageTimeMs = System.currentTimeMillis();
        this.lastDamageEffectType = damageEffectType != null ? damageEffectType : DamageEffectType.MELEE;
        this.health -= amount;
        applyMoraleLoss(amount);
        reduceCohesion((int)Math.ceil(amount * 0.25));
        if (this.health <= 0) {
            // unit death
            this.unitSize = 0;
            this.moving = false;
        }
        checkSurrenderState();
    }

    public void applyMoraleLoss(double damageAmount) {
        // scale morale loss by damage relative to maxHealth
        double loss = (damageAmount / Math.max(1.0, maxHealth)) * 20.0;
        this.morale = (byte)(Math.max(0, (int)(this.morale - loss)));
    }

    private void reduceCohesion(int amount) { this.cohesion = (byte)Math.max(0, this.cohesion - amount); }

    public synchronized void gainExperience(double amount) {
        this.experiencePoints += amount;
        // level up
        int newLevel = (int)(this.experiencePoints / 100.0);
        if (newLevel > this.experienceLevelNum) {
            this.experienceLevelNum = newLevel;
            assignExperienceLevel();
            // small stat increase on level
            this.attackPower += 0.5 * newLevel;
            this.defense += 0.3 * newLevel;
            this.maxHealth += 5.0 * newLevel;
            this.health = Math.min(this.health + 2.0 * newLevel, this.maxHealth);
        }
    }

    private void updateCombatState() {
        int secondsElapsed = consumeConditionUpdateSeconds();
        if (secondsElapsed == 0) {
            return;
        }
        long conditionWindowEndMs = lastConditionUpdateMs;
        long conditionWindowStartMs = conditionWindowEndMs - secondsElapsed * 1000L;
        int staminaValue = Byte.toUnsignedInt(stamina);
        int moraleValue = Byte.toUnsignedInt(morale);
        int cohesionValue = Byte.toUnsignedInt(cohesion);
        for (int second = 0; second < secondsElapsed; second++) {
            long secondTimestampMs = conditionWindowStartMs + (second + 1L) * 1000L;
            int[] updatedValues = updateConditionsForSecond(staminaValue, moraleValue, cohesionValue,
                    secondTimestampMs);
            staminaValue = updatedValues[0];
            moraleValue = updatedValues[1];
            cohesionValue = updatedValues[2];
            if (health < maxHealth && !fighting && !isRecoveryBlockedAt(secondTimestampMs)) {
                health = Math.min(maxHealth, health + random.nextDouble());
            }
        }
        stamina = (byte)staminaValue;
        cohesion = (byte)cohesionValue;
        morale = (byte)moraleValue;
        checkSurrenderState();
    }

    private int consumeConditionUpdateSeconds() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastConditionUpdateMs;
        if (elapsedMs < 1000) {
            return 0;
        }
        int secondsElapsed = (int)(elapsedMs / 1000L);
        lastConditionUpdateMs += secondsElapsed * 1000L;
        return secondsElapsed;
    }

    private int[] updateConditionsForSecond(int staminaValue, int moraleValue, int cohesionValue,
            long conditionTimestampMs) {
        double fortInfluence = getFortZoneInfluence();
        int moraleSupport = fortInfluence >= 0.45 ? 1 : 0;
        int cohesionSupport = fortInfluence >= 0.75 ? 1 : 0;
        boolean outOfSupply = !supplied;
        boolean recoveryBlocked = isRecoveryBlockedAt(conditionTimestampMs);
        if (fighting) {
            return updateFightingConditions(staminaValue, moraleValue, cohesionValue, moraleSupport, cohesionSupport,
                    outOfSupply, recoveryBlocked);
        }
        if (moving) {
            return updateMovingConditions(staminaValue, moraleValue, cohesionValue, fortInfluence, outOfSupply,
                    recoveryBlocked);
        }
        return updateIdleConditions(staminaValue, moraleValue, cohesionValue, moraleSupport, cohesionSupport,
                outOfSupply, recoveryBlocked);
    }

    private int[] updateFightingConditions(int staminaValue, int moraleValue, int cohesionValue, int moraleSupport,
            int cohesionSupport, boolean outOfSupply, boolean recoveryBlocked) {
        staminaValue = Math.max(0, staminaValue - (outOfSupply ? 3 : 2));
        cohesionValue = Math.max(0, cohesionValue - 2 - (moraleValue < 40 ? 1 : 0) - (outOfSupply ? 1 : 0));
        moraleValue = Math.max(0, moraleValue - (outOfSupply ? 2 : 1));
        if (!recoveryBlocked) {
            moraleValue = Math.min(100, moraleValue + moraleSupport);
        }
        cohesionValue = Math.min(100, cohesionValue + cohesionSupport);
        return new int[] {staminaValue, moraleValue, cohesionValue};
    }

    private int[] updateMovingConditions(int staminaValue, int moraleValue, int cohesionValue, double fortInfluence,
            boolean outOfSupply, boolean recoveryBlocked) {
        staminaValue = Math.max(0, staminaValue - (outOfSupply ? 2 : 1));
        cohesionValue = Math.max(0, cohesionValue - (moraleValue < 30 ? 1 : 0) - (outOfSupply ? 1 : 0));
        if (outOfSupply) {
            moraleValue = Math.max(0, moraleValue - 1);
        } else if (!recoveryBlocked) {
            moraleValue = Math.min(100, moraleValue + (fortInfluence >= 0.85 ? 1 : 0));
        }
        return new int[] {staminaValue, moraleValue, cohesionValue};
    }

    private int[] updateIdleConditions(int staminaValue, int moraleValue, int cohesionValue, int moraleSupport,
            int cohesionSupport, boolean outOfSupply, boolean recoveryBlocked) {
        if (!recoveryBlocked) {
            staminaValue = Math.min(100, staminaValue + (outOfSupply ? 1 : 3));
        }
        cohesionValue = Math.min(100, cohesionValue + (outOfSupply ? 1 : 2) + cohesionSupport);
        if (supplied) {
            if (!recoveryBlocked) {
                moraleValue = Math.min(100, moraleValue + 1 + moraleSupport);
            }
        } else {
            moraleValue = Math.max(0, moraleValue - (foraging ? 0 : 1));
        }
        return new int[] {staminaValue, moraleValue, cohesionValue};
    }

    private boolean isRecoveryBlockedAt(long timestampMs) {
        return timestampMs - lastDamageTimeMs < RECOVERY_BLOCK_AFTER_DAMAGE_MS;
    }

    private double getFortZoneInfluence() {
        TileMap activeTileMap = UnitManager.getActiveMap();
        return activeTileMap != null ? activeTileMap.getFortInfluenceAtWorld(posX, posY) : 0.0;
    }

    public int getFoodConsumptionPerTick() {
        if (unitSize <= 0 || health <= 0 || surrendered) {
            return 0;
        }
        int baseConsumption = Math.max(1, unitSize / 700);
        return switch (soldierClass) {
        case CAVALRY, HORSE_ARCHER -> baseConsumption + 1;
        case ARCHER, INFANTRY -> baseConsumption;
        };
    }

    public int getFoodCarryCapacity() {
        int baseCapacity = Math.max(12, unitSize / 90);
        return switch (soldierClass) {
        case CAVALRY, HORSE_ARCHER -> baseCapacity + 10;
        case ARCHER -> baseCapacity + 4;
        case INFANTRY -> baseCapacity + 6;
        };
    }

    public int collectForage(TileMap map) {
        if (!foraging || moving || fighting || surrendered || health <= 0 || map == null) {
            return 0;
        }
        if (map.getStructureTypeAtWorld(posX, posY) != StructureType.NONE) {
            return 0;
        }
        TerrainType terrainType = map.getTerrainTypeAtWorld(posX, posY);
        double yieldMultiplier = getForageYieldMultiplier(terrainType);
        if (yieldMultiplier <= 0.0) {
            return 0;
        }
        int capacityRemaining = Math.max(0, getFoodCarryCapacity() - carriedFood);
        if (capacityRemaining == 0) {
            return 0;
        }
        double classMultiplier = switch (soldierClass) {
        case CAVALRY, HORSE_ARCHER -> 0.88;
        case ARCHER -> 0.94;
        case INFANTRY -> 1.0;
        };
        int gatheredFood = Math.max(1,
                (int)Math.round(Math.max(1.0, unitSize / 450.0) * yieldMultiplier * classMultiplier));
        gatheredFood = Math.min(gatheredFood, capacityRemaining);
        carriedFood += gatheredFood;
        return gatheredFood;
    }

    private double getForageYieldMultiplier(TerrainType terrainType) {
        if (terrainType == null) {
            return 0.0;
        }
        return switch (terrainType) {
        case FLAT_GRASS -> 1.0;
        case HILLY_GRASS -> 0.82;
        case FLAT_FOREST -> 1.15;
        case HILLY_FOREST -> 0.96;
        case MOUNTAINOUS_FOREST -> 0.72;
        case MARSHLAND -> 0.55;
        case FORESTED_MARSHLAND -> 0.66;
        case FLAT_MUD -> 0.44;
        case FORESTED_MUD -> 0.56;
        case HILLY_MUD -> 0.40;
        case MOUNTAIN_PASS -> 0.32;
        case RIVER, IMPASSABLE_MOUNTAIN -> 0.0;
        };
    }

    public int transferExcessCarriedFoodToReserve() {
        int retainedFood = Math.max(6, getFoodCarryCapacity() / 3);
        int transferredFood = Math.max(0, carriedFood - retainedFood);
        carriedFood -= transferredFood;
        return transferredFood;
    }

    public int consumeCarriedFood(int requestedAmount) {
        if (requestedAmount <= 0 || carriedFood <= 0) {
            return 0;
        }
        int consumedAmount = Math.min(requestedAmount, carriedFood);
        carriedFood -= consumedAmount;
        return consumedAmount;
    }

    private void checkSurrenderState() {
        if (Byte.toUnsignedInt(morale) == 0 || Byte.toUnsignedInt(stamina) == 0) {
            surrendered = true;
            moving = false;
            foraging = false;
            selected = false;
            targetCoords = null;
            queuedTargetCoords = null;
            queuedPath = null;
            path = null;
            clearEngagementTarget();
            pathfindingControlled = false;
            queuedPathfindingControlled = false;
        }
    }

    private void processQueuedOrder() {
        if (queuedTargetCoords == null || System.currentTimeMillis() < queuedOrderReadyTimeMs) {
            return;
        }
        targetCoords = queuedTargetCoords;
        path = queuedPath != null ? new ArrayList<>(queuedPath) : null;
        pathfindingControlled = queuedPathfindingControlled;
        moving = (path != null && !path.isEmpty()) || targetCoords != null;
        queuedTargetCoords = null;
        queuedPath = null;
        queuedPathfindingControlled = false;
        resetMovementProgress();
    }

    private long calculateOrderDelayMs() {
        int cohesionValue = Byte.toUnsignedInt(cohesion);
        int moraleValue = Byte.toUnsignedInt(morale);
        double cohesionPenalty = (100 - cohesionValue) * 12.0;
        double moralePenalty = (100 - moraleValue) * 4.0;
        return Math.round(cohesionPenalty + moralePenalty);
    }

    private void queueOrder(Point coord, List<Point> plannedPath) {
        if (coord == null || surrendered || Byte.toUnsignedInt(cohesion) == 0) {
            return;
        }
        foraging = false;
        Point clampedTarget = clampPointToWorldBounds(coord, UnitManager.getActiveMap());
        updateFacingFromVector(clampedTarget.x - posX, clampedTarget.y - posY);
        queuedTargetCoords = clampedTarget;
        queuedPath = plannedPath != null ? new ArrayList<>(plannedPath) : null;
        queuedPathfindingControlled = plannedPath != null && !plannedPath.isEmpty();
        queuedOrderReadyTimeMs = System.currentTimeMillis() + calculateOrderDelayMs();
        if (queuedOrderReadyTimeMs <= System.currentTimeMillis()) {
            processQueuedOrder();
        }
    }

    private void updateFacingFromVector(double dx, double dy) {
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            return;
        }
        facingAngleRadians = Math.atan2(dy, dx);
    }

    private void faceUnit(Unit other) {
        if (other == null) {
            return;
        }
        updateFacingFromVector(other.getX() - posX, other.getY() - posY);
    }

    public SoldierClass getSoldierClass() { return soldierClass; }

    public void setSoldierClass(SoldierClass soldierClass) {
        this.soldierClass = alignClassWithType(soldierClass, soldierType);
    }

    public SoldierCulture getSoldierCulture() { return soldierCulture; }

    public void setSoldierCulture(SoldierCulture soldierCulture) { this.soldierCulture = soldierCulture; }

    public SoldierType getSoldierType() { return soldierType; }

    public void setSoldierType(SoldierType soldierType) {
        this.soldierType = soldierType;
        this.soldierClass = alignClassWithType(soldierClass, soldierType);
        this.weaponMode = defaultWeaponModeForType();
        assignWeaponEffectiveness();
        assignArmorEffectiveness();
        assignMobility();
        assignMovementSpeed();
    }

    public int getUnitSize() { return unitSize; }

    public void setUnitSize(int unitSize) { this.unitSize = normalizeUnitSize(unitSize); }

    public void increaseUnitSize(int numSoldiers) { this.unitSize = normalizeUnitSize(this.unitSize + numSoldiers); }

    public ExperienceLevel getExperienceLevel() { return experienceLevel; }

    public void setExperienceLevel(ExperienceLevel experienceLevel) { this.experienceLevel = experienceLevel; }

    public int getExperienceLevelNum() { return experienceLevelNum; }

    public void setExperienceLevelNum(int experienceLevelNum) { this.experienceLevelNum = experienceLevelNum; }

    public double getExperiencePoints() { return experiencePoints; }

    public void setExperiencePoints(double experiencePoints) { this.experiencePoints = experiencePoints; }

    public void incrementExperiencePoints() { this.experiencePoints += Math.random() * 5 + 1; }

    public byte getWeaponEffectiveness() {
        if (isRomanHeavyInfantryRangedJavelinMode()) {
            return (byte)Math.max(1, weaponEffectiveness - 1);
        }
        if (isRomanHeavyInfantryMeleeJavelinMode()) {
            return (byte)Math.min(10, weaponEffectiveness + 1);
        }
        return weaponEffectiveness;
    }

    public void setWeaponEffectiveness(byte weaponEffectiveness) { this.weaponEffectiveness = weaponEffectiveness; }

    public byte getArmorEffectiveness() { return armorEffectiveness; }

    public void setArmorEffectiveness(byte armorEffectiveness) { this.armorEffectiveness = armorEffectiveness; }

    public byte getMobility() { return mobility; }

    public void setMobility(byte mobility) { this.mobility = mobility; }

    public byte getMorale() { return morale; }

    public void setMorale(byte morale) {
        this.morale = (byte)Math.clamp(Byte.toUnsignedInt(morale), 0, 100);
        checkSurrenderState();
    }

    public byte getCohesion() { return cohesion; }

    public void setCohesion(byte cohesion) { this.cohesion = (byte)Math.clamp(Byte.toUnsignedInt(cohesion), 0, 100); }

    public byte getStamina() { return stamina; }

    public void setStamina(byte stamina) {
        this.stamina = (byte)Math.clamp(Byte.toUnsignedInt(stamina), 0, 100);
        checkSurrenderState();
    }

    public int getX() { return coords.x; }

    public int getY() { return coords.y; }

    public Point getCoords() { return coords; }

    public boolean isColliding() { return colliding; }

    public void setColliding(boolean collision) { this.colliding = collision; }

    public boolean isSelected() { return selected; }

    public void setSelected(boolean selected) { this.selected = selected; }

    public BufferedImage getImage() { return image; }

    public Point getTargetCoords() { return targetCoords; }

    public Unit getEngagementTarget() { return engagementTarget; }

    public long getLastDamageTimeMs() { return lastDamageTimeMs; }

    public DamageEffectType getLastDamageEffectType() { return lastDamageEffectType; }

    public double getTargetX() { return (targetCoords != null) ? targetCoords.x : coords.x; }

    public double getTargetY() { return (targetCoords != null) ? targetCoords.y : coords.y; }

    public void setTargetCoords(Point coords) {
        clearEngagementTarget();
        queueOrder(coords, null);
    }

    public void setTargetCoords(int x, int y) { setTargetCoords(new Point(x, y)); }

    public void setTargetCoordsComputePath(Point coord, TileMap map) { issuePathOrder(coord, map, true); }

    public void setEngagementTarget(Unit target, TileMap map) {
        if (target == null || target == this || !isEnemy(target)) {
            return;
        }
        engagementTarget = target;
        engagementOrdered = true;
        lastEngagementPathUpdateMs = 0L;
        lastEngagementTargetCoords = null;
        updateFacingFromVector(target.getX() - posX, target.getY() - posY);
        updateEngagementOrder();
    }

    public void setDesiredFacingAngleRadians(Double desiredFacingAngleRadians) {
        this.desiredFacingAngleRadians = desiredFacingAngleRadians;
    }

    public void setFacingAngleRadians(double facingAngleRadians) {
        this.facingAngleRadians = facingAngleRadians;
        this.desiredFacingAngleRadians = null;
    }

    public double getSpeed() { return speed; }

    public void setSpeed(double speed) { this.speed = speed; }

    public Color getColor() { return color; }

    public void setColor(Color color) { this.color = color; }

    public boolean isMovingNorth() { return movingNorth; }

    public boolean isMovingEast() { return movingEast; }

    public boolean isMovingSouth() { return movingSouth; }

    public boolean isMovingWest() { return movingWest; }

    public boolean isSupplied() { return supplied; }

    public void setSupplied(boolean supplied) { this.supplied = supplied; }

    public boolean isForaging() { return foraging; }

    public void setForaging(boolean foraging) {
        if (surrendered || health <= 0) {
            this.foraging = false;
            return;
        }
        this.foraging = foraging;
        if (!foraging) {
            return;
        }
        moving = false;
        skirmishRetreating = false;
        targetCoords = null;
        queuedTargetCoords = null;
        queuedPath = null;
        path = null;
        clearEngagementTarget();
        pathfindingControlled = false;
        queuedPathfindingControlled = false;
    }

    public int getCarriedFood() { return carriedFood; }

    public boolean hasSurrendered() { return surrendered; }

    public double getFacingAngleRadians() { return facingAngleRadians; }

    public double getFacingVectorX() { return Math.cos(facingAngleRadians); }

    public double getFacingVectorY() { return Math.sin(facingAngleRadians); }

    public boolean supportsWeaponModeToggle() { return soldierType == SoldierType.ROMAN_HEAVY_INFANTRY; }

    public boolean isRomanHeavyInfantry() { return soldierType == SoldierType.ROMAN_HEAVY_INFANTRY; }

    public boolean isRomanHeavyInfantryRangedJavelinMode() {
        return isRomanHeavyInfantry() && weaponMode == WeaponMode.JAVELIN_RANGED;
    }

    public boolean isRomanHeavyInfantryMeleeJavelinMode() {
        return isRomanHeavyInfantry() && weaponMode == WeaponMode.JAVELIN_MELEE;
    }

    public void cycleWeaponMode() {
        if (!supportsWeaponModeToggle()) {
            return;
        }
        weaponMode = switch (weaponMode) {
        case SWORD -> WeaponMode.JAVELIN_RANGED;
        case JAVELIN_RANGED -> WeaponMode.JAVELIN_MELEE;
        case JAVELIN_MELEE, STANDARD -> WeaponMode.SWORD;
        };
    }

    public WeaponMode getWeaponMode() { return weaponMode; }

    public String getWeaponModeDisplayName() {
        return switch (weaponMode) {
        case SWORD -> "Sword";
        case JAVELIN_RANGED -> "Ranged Javelin";
        case JAVELIN_MELEE -> "Melee Javelin";
        case STANDARD -> "Standard";
        };
    }
}