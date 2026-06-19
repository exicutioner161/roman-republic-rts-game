package combat;

import units.ExperienceTable;
import units.Unit;
import units.UnitManager;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.TileMap;

public class CombatSystem {
    private CombatSystem() {}

    public static double calculateBattlePower(Unit attacker, Unit defender) {
        if (attacker == null) {
            return 0.0;
        }
        double manpower = attacker.getUnitSize() / 1000.0;
        double experience = ExperienceTable.getExperienceMultiplier(attacker.getExperienceLevelNum());
        double weaponStrength = attacker.getWeaponEffectiveness() + attacker.getAttackPower() * 0.35;
        double armorStrength = attacker.getArmorEffectiveness() + attacker.getDefense() * 0.35;
        double equipment = 1.0 + weaponStrength * 0.035 + armorStrength * 0.025;
        double matchup = defender != null
                ? getClassMultiplier(attacker, defender) * getTypeSpecificModifier(attacker, defender)
                        * getArmorMitigationFactor(attacker, defender) * getChargeResistanceFactor(attacker, defender)
                : 1.0;
        return Math.max(0.1, manpower * experience * equipment * (0.60 + getConditionRatio(attacker) * 0.40) * matchup);
    }

    public static double calculateDamage(Unit attacker, Unit defender) {
        if (attacker == null || defender == null) {
            return 0.0;
        }
        double attackerRating = attacker.getWeaponEffectiveness() + attacker.getAttackPower();
        double defenderRating = defender.getArmorEffectiveness() + defender.getDefense();
        double qualityFactor = Math.clamp(attackerRating / Math.max(4.0, defenderRating), 0.65, 1.75);
        double manpowerFactor = Math
                .clamp(Math.sqrt(attacker.getUnitSize() / Math.max(1.0, defender.getUnitSize() * 1.0)), 0.80, 1.35);
        double experienceFactor = Math
                .clamp(1.0 + (attacker.getExperienceLevelNum() - defender.getExperienceLevelNum()) * 0.05, 0.80, 1.35);
        double equipmentFactor = Math
                .clamp(1.0 + (attacker.getWeaponEffectiveness() - defender.getArmorEffectiveness()) * 0.06, 0.75, 1.35);
        double conditionFactor = 0.65 + getConditionRatio(attacker) * 0.35;
        double resistanceFactor = 1.15 - getConditionRatio(defender) * 0.35;
        double damage = 0.9 * qualityFactor * manpowerFactor * experienceFactor * equipmentFactor
                * getClassMultiplier(attacker, defender) * getTypeSpecificModifier(attacker, defender)
                * getArmorMitigationFactor(attacker, defender) * getChargeResistanceFactor(attacker, defender)
                * getFlankMultiplier(attacker, defender) * getAttackerFacingFactor(attacker, defender)
                * getTerrainCombatFactor(attacker, defender) * getEngagementFactor(attacker, defender)
                * getAttackStyleDamageFactor(attacker, defender) * conditionFactor * resistanceFactor;
        return Math.max(0.5, damage);
    }

    private static double getEngagementFactor(Unit attacker, Unit defender) {
        double engagedSize = Math.min(attacker.getUnitSize(), defender.getUnitSize());
        if (usesProjectilePressure(attacker)) {
            return Math.clamp(engagedSize / 2000.0, 0.35, 2.75);
        }
        return Math.clamp(engagedSize / 1000.0, 0.55, 4.5);
    }

    private static double getAttackStyleDamageFactor(Unit attacker, Unit defender) {
        if (attacker == null || defender == null || usesProjectilePressure(attacker)) {
            return 1.0;
        }
        if (usesProjectilePressure(defender)) {
            return 1.25;
        }
        return 2.15;
    }

    private static double getTerrainCombatFactor(Unit attacker, Unit defender) {
        TileMap activeMap = UnitManager.getActiveMap();
        if (activeMap == null || attacker == null || defender == null) {
            return 1.0;
        }
        TerrainType attackerTerrain = activeMap.getTerrainTypeAtWorld(attacker.getX(), attacker.getY());
        TerrainType defenderTerrain = activeMap.getTerrainTypeAtWorld(defender.getX(), defender.getY());
        StructureType attackerStructure = activeMap.getStructureTypeAtWorld(attacker.getX(), attacker.getY());
        StructureType defenderStructure = activeMap.getStructureTypeAtWorld(defender.getX(), defender.getY());
        int heightDelta = attackerTerrain.getHeightLevel() - defenderTerrain.getHeightLevel();
        double factor = 1.0;
        if (heightDelta > 0) {
            factor *= 1.0 + heightDelta * 0.10;
        } else if (heightDelta < 0) {
            factor *= Math.max(0.62, 1.0 - (-heightDelta) * 0.12);
        }
        if (defenderTerrain.isElevated()) {
            factor *= Math.max(0.65, 1.0 - defenderTerrain.getHeightLevel() * 0.08);
        }
        if (defenderTerrain.isForest()) {
            factor *= 0.93;
        }
        factor *= defenderStructure.getDefenderDamageMultiplier();
        if (usesProjectilePressure(attacker)) {
            factor *= activeMap.getFortMissileCoverMultiplierAtWorld(defender.getX(), defender.getY());
        }
        if (attackerStructure.isFort() && usesProjectilePressure(attacker)) {
            factor *= 1.06;
        }
        factor *= getMountedTerrainFactor(attacker.getSoldierClass(), attackerTerrain, true);
        factor *= getMountedTerrainFactor(defender.getSoldierClass(), defenderTerrain, false);
        return Math.clamp(factor, 0.45, 1.55);
    }

    private static double getMountedTerrainFactor(Unit.SoldierClass soldierClass, TerrainType terrainType,
            boolean attacking) {
        if (soldierClass != Unit.SoldierClass.CAVALRY && soldierClass != Unit.SoldierClass.HORSE_ARCHER) {
            return 1.0;
        }
        return resolveMountedTerrainFactor(terrainType, attacking);
    }

    private static double resolveMountedTerrainFactor(TerrainType terrainType, boolean attacking) {
        if (terrainType.isWetland()) {
            return getWetlandMountedFactor(attacking);
        }
        if (terrainType.isMudTerrain()) {
            return getMudMountedFactor(terrainType, attacking);
        }
        if (terrainType.isFlatOpenTerrain()) {
            return attacking ? 1.12 : 1.06;
        }
        if (terrainType.isForest()) {
            return attacking ? 0.74 : 0.82;
        }
        if (terrainType.isRiver()) {
            return attacking ? 0.68 : 0.78;
        }
        if (terrainType.isMountainTerrain() || terrainType.isHillTerrain()) {
            return attacking ? 0.76 - terrainType.getHeightLevel() * 0.06 : 0.84 - terrainType.getHeightLevel() * 0.04;
        }
        return 1.0;
    }

    private static double getWetlandMountedFactor(boolean attacking) { return attacking ? 0.58 : 0.68; }

    private static double getMudMountedFactor(TerrainType terrainType, boolean attacking) {
        double factor = getBaseMudMountedFactor(terrainType, attacking);
        if (terrainType.isForest()) {
            factor -= attacking ? 0.08 : 0.06;
        }
        return factor;
    }

    private static double getBaseMudMountedFactor(TerrainType terrainType, boolean attacking) {
        if (terrainType.isHillTerrain()) {
            return attacking ? 0.74 : 0.82;
        }
        return attacking ? 0.84 : 0.92;
    }

    private static double getClassMultiplier(Unit attacker, Unit defender) {
        return switch (attacker.getSoldierClass()) {
        case INFANTRY -> switch (defender.getSoldierClass()) {
        case CAVALRY -> 1.28;
        case HORSE_ARCHER -> 1.18;
        case ARCHER -> 0.55;
        default -> 1.0;
        };
        case ARCHER -> switch (defender.getSoldierClass()) {
        case INFANTRY -> 1.90;
        case CAVALRY -> 0.80;
        case HORSE_ARCHER -> 0.92;
        default -> 1.0;
        };
        case CAVALRY -> switch (defender.getSoldierClass()) {
        case ARCHER -> 1.30;
        case HORSE_ARCHER -> 1.18;
        case INFANTRY -> 0.86;
        default -> 1.0;
        };
        case HORSE_ARCHER -> switch (defender.getSoldierClass()) {
        case INFANTRY -> 1.12;
        case ARCHER -> 1.06;
        case CAVALRY -> 0.82;
        default -> 1.0;
        };
        };
    }

    private static double getTypeSpecificModifier(Unit attacker, Unit defender) {
        if (attacker.getSoldierClass() == Unit.SoldierClass.ARCHER
                && defender.getSoldierType() == Unit.SoldierType.ROMAN_HEAVY_INFANTRY) {
            return 1.65;
        }
        if (attacker.getSoldierType() == Unit.SoldierType.ROMAN_SPEARMAN
                && defender.getSoldierClass() == Unit.SoldierClass.CAVALRY) {
            return 1.35;
        }
        if (attacker.getSoldierClass() == Unit.SoldierClass.CAVALRY
                && defender.getSoldierType() == Unit.SoldierType.ROMAN_SPEARMAN) {
            return 0.75;
        }
        if (attacker.getSoldierType() == Unit.SoldierType.ROMAN_HEAVY_INFANTRY
                && defender.getSoldierClass() == Unit.SoldierClass.ARCHER) {
            return 0.50;
        }
        double heavyInfantryAttackModifier = getRomanHeavyInfantryAttackModifier(attacker, defender);
        if (heavyInfantryAttackModifier != 1.0) {
            return heavyInfantryAttackModifier;
        }
        double cavalryIntoHeavyModifier = getCavalryIntoRomanHeavyInfantryModifier(attacker, defender);
        if (cavalryIntoHeavyModifier != 1.0) {
            return cavalryIntoHeavyModifier;
        }
        return 1.0;
    }

    private static double getRomanHeavyInfantryAttackModifier(Unit attacker, Unit defender) {
        if (attacker.getSoldierType() != Unit.SoldierType.ROMAN_HEAVY_INFANTRY) {
            return 1.0;
        }
        if (attacker.isRomanHeavyInfantryMeleeJavelinMode()) {
            return defender.getSoldierClass() == Unit.SoldierClass.CAVALRY ? 1.55 : 0.92;
        }
        if (attacker.isRomanHeavyInfantryRangedJavelinMode()
                && defender.getSoldierClass() == Unit.SoldierClass.CAVALRY) {
            return 1.12;
        }
        return defender.getSoldierClass() == Unit.SoldierClass.CAVALRY ? 1.20 : 1.0;
    }

    private static double getCavalryIntoRomanHeavyInfantryModifier(Unit attacker, Unit defender) {
        if (attacker.getSoldierClass() != Unit.SoldierClass.CAVALRY
                || defender.getSoldierType() != Unit.SoldierType.ROMAN_HEAVY_INFANTRY) {
            return 1.0;
        }
        if (defender.isRomanHeavyInfantryMeleeJavelinMode()) {
            return 0.62;
        }
        if (defender.isRomanHeavyInfantryRangedJavelinMode()) {
            return 0.84;
        }
        return 0.78;
    }

    private static double getArmorMitigationFactor(Unit attacker, Unit defender) {
        int armor = Byte.toUnsignedInt(defender.getArmorEffectiveness());
        if (armor <= 1) {
            return 1.0;
        }
        int armorAboveBaseline = armor - 1;
        if (usesProjectilePressure(attacker)) {
            return Math.clamp(1.0 - armorAboveBaseline * 0.08, 0.60, 1.0);
        }
        if (isCavalryChargeThreat(attacker)) {
            return Math.clamp(1.0 - armorAboveBaseline * 0.06, 0.68, 1.0);
        }
        return 1.0;
    }

    private static boolean usesProjectilePressure(Unit attacker) {
        return switch (attacker.getSoldierClass()) {
        case ARCHER, HORSE_ARCHER -> true;
        default -> attacker.getSoldierType() == Unit.SoldierType.EGYPTIAN_CHARIOT
                || attacker.isRomanHeavyInfantryRangedJavelinMode();
        };
    }

    private static boolean isCavalryChargeThreat(Unit attacker) {
        return attacker.getSoldierClass() == Unit.SoldierClass.CAVALRY;
    }

    private static double getChargeResistanceFactor(Unit attacker, Unit defender) {
        if (!isCavalryChargeThreat(attacker) || defender == null) {
            return 1.0;
        }
        double facingDot = getDefenderFacingDotTowardAttacker(attacker, defender);
        double readiness = getBraceReadiness(defender);
        double formationResistance = getFormationChargeResistance(defender);
        if (facingDot >= 0.55) {
            return Math.clamp(1.0 - formationResistance * readiness, 0.38, 0.95);
        }
        if (facingDot >= 0.15) {
            return Math.clamp(1.0 - formationResistance * readiness * 0.45, 0.62, 1.0);
        }
        if (facingDot > -0.35) {
            return Math.clamp(1.0 + (0.18 - formationResistance * 0.10), 1.02, 1.18);
        }
        return Math.clamp(1.0 + (0.30 - formationResistance * 0.08), 1.12, 1.30);
    }

    private static double getFormationChargeResistance(Unit defender) {
        if (defender.getSoldierClass() != Unit.SoldierClass.INFANTRY) {
            return 0.10;
        }
        if (defender.isRomanHeavyInfantryMeleeJavelinMode()) {
            return 0.42;
        }
        return switch (defender.getSoldierType()) {
        case ROMAN_HEAVY_INFANTRY, ROMAN_SPEARMAN, EGYPTIAN_INFANTRY, PARTHIAN_INFANTRY -> 0.34;
        case GALLIC_INFANTRY -> 0.24;
        case ROMAN_LIGHT_INFANTRY -> 0.16;
        default -> 0.20;
        };
    }

    private static double getConditionRatio(Unit unit) {
        if (unit == null) {
            return 0.0;
        }
        return (Byte.toUnsignedInt(unit.getMorale()) + Byte.toUnsignedInt(unit.getCohesion())
                + Byte.toUnsignedInt(unit.getStamina())) / 300.0;
    }

    private static double getBraceReadiness(Unit defender) {
        double cohesion = Byte.toUnsignedInt(defender.getCohesion()) / 100.0;
        double stamina = Byte.toUnsignedInt(defender.getStamina()) / 100.0;
        double morale = Byte.toUnsignedInt(defender.getMorale()) / 100.0;
        double readiness = 0.45 + cohesion * 0.25 + stamina * 0.15 + morale * 0.15;
        if (defender.isMoving()) {
            readiness *= 0.72;
        }
        return Math.clamp(readiness, 0.35, 1.0);
    }

    private static double getFacingDotToward(Unit unit, double targetX, double targetY) {
        if (unit == null) {
            return 0.0;
        }
        double dx = targetX - unit.getX();
        double dy = targetY - unit.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return 0.0;
        }
        double toTargetX = dx / len;
        double toTargetY = dy / len;
        return unit.getFacingVectorX() * toTargetX + unit.getFacingVectorY() * toTargetY;
    }

    private static double getDefenderFacingDotTowardAttacker(Unit attacker, Unit defender) {
        if (attacker == null || defender == null) {
            return 0.0;
        }
        return getFacingDotToward(defender, attacker.getX(), attacker.getY());
    }

    private static double getFlankMultiplier(Unit attacker, Unit defender) {
        double facingDot = getDefenderFacingDotTowardAttacker(attacker, defender);
        if (facingDot < -0.45) {
            return 1.30;
        }
        if (Math.abs(facingDot) < 0.35) {
            return 1.15;
        }
        return 1.0;
    }

    private static double getAttackerFacingFactor(Unit attacker, Unit defender) {
        if (attacker == null || defender == null) {
            return 1.0;
        }
        double facingDot = getFacingDotToward(attacker, defender.getX(), defender.getY());
        return Math.clamp(0.85 + Math.max(0.0, facingDot) * 0.20, 0.85, 1.05);
    }
}