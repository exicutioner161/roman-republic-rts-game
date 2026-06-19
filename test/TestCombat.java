package test;

import combat.CombatSystem;
import java.awt.Point;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import units.Unit;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import units.UnitManager;

public class TestCombat {
        public static void main(String[] args) {
                if (args.length > 0) {
                        System.out.println("Ignoring command-line arguments for TestCombat.");
                }
                verifyCounterRelationships();
                verifyArmorMitigation();
                verifyRomanHeavyInfantryModes();
                verifyFootprintGeometryUsesExactPosition();
                verifyIncomingAttackLocksFirstAttacker();
                verifyMeleeCombatAllowsNearContact();
                verifyMeleeDamageAfterDiagonalApproach();
                verifyDamageParticleEffectTypes();
                verifyMeleeDamageIsVisible();
                Unit a = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY,
                                100, 100, 1000, 1);
                Unit b = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY, 110,
                                110, 1000, 1);
                System.out.println("Initial A health=" + a.getHealth() + " B health=" + b.getHealth());
                final int steps = 20;
                try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                        final int[] counter = {0};
                        Runnable task = () -> {
                                int i = counter[0];
                                a.attack(b);
                                b.attack(a);
                                System.out.println("Step " + i + " A=" + a.getHealth() + " B=" + b.getHealth());
                                counter[0]++;
                                if (counter[0] >= steps) {
                                        scheduler.shutdown();
                                }
                        };
                        // Run the task every 200ms without calling Thread.sleep
                        // inside the
                        // loop
                        scheduler.scheduleAtFixedRate(task, 0, 200, TimeUnit.MILLISECONDS);
                        try {
                                // Wait until all steps complete or timeout
                                scheduler.awaitTermination(steps * 250L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                        }
                }
        }

        private static void verifyFootprintGeometryUsesExactPosition() {
                Unit mover = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 1);
                mover.setTargetCoords(new Point(230, 197));
                mover.update(new Unit[] {mover});
                double centerX = mover.getBounds().getCenterX();
                double centerY = mover.getBounds().getCenterY();
                require(Math.abs(centerX - mover.getCoords().x) > 0.01
                                || Math.abs(centerY - mover.getCoords().y) > 0.01,
                                "Expected footprint geometry to preserve fractional movement instead of snapping to integer coords.");
        }

        private static void verifyMeleeDamageAfterDiagonalApproach() {
                Unit attacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 1);
                Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 182, 154, 1000, 1);
                attacker.setTargetCoords(new Point(defender.getCoords()));
                double defenderStartHealth = defender.getHealth();
                try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                        final int[] counter = {0};
                        Runnable task = () -> {
                                Unit[] snapshot = {attacker, defender};
                                attacker.update(snapshot);
                                defender.update(snapshot);
                                counter[0]++;
                                if (counter[0] >= 120 || defender.getHealth() < defenderStartHealth) {
                                        scheduler.shutdown();
                                }
                        };
                        scheduler.scheduleAtFixedRate(task, 0, 50, TimeUnit.MILLISECONDS);
                        try {
                                scheduler.awaitTermination(7000L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                        }
                }
                require(defender.getHealth() < defenderStartHealth,
                                "Expected a melee unit to inflict damage after reaching contact on a diagonal live approach.");
        }

        private static void verifyMeleeCombatAllowsNearContact() {
                Unit attacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 1);
                Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 160, 100, 1000, 1);
                double defenderStartHealth = defender.getHealth();
                Unit[] snapshot = {attacker, defender};
                for (int step = 0; step < 6 && defender.getHealth() >= defenderStartHealth; step++) {
                        attacker.update(snapshot);
                        defender.update(snapshot);
                }
                require(defender.getHealth() < defenderStartHealth,
                                "Expected melee units with a small footprint gap to exchange attacks without exact overlap.");
        }

        private static void verifyIncomingAttackLocksFirstAttacker() {
                Unit firstAttacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 92, 100, 1000, 1);
                Unit secondAttacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 108, 100, 1000, 1);
                Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 110, 110, 1000, 1);
                firstAttacker.attack(defender);
                secondAttacker.attack(defender);
                require(defender.getEngagementTarget() == firstAttacker,
                                "Expected a unit to lock onto the first attacker instead of switching to later attackers.");
                Unit[] snapshot = {firstAttacker, secondAttacker, defender};
                for (int step = 0; step < 8; step++) {
                        defender.update(snapshot);
                }
                require(defender.getEngagementTarget() == firstAttacker,
                                "Expected the first incoming attacker lock to remain stable across update ticks.");
        }

        private static void verifyMeleeDamageIsVisible() {
                Unit attacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 1);
                Unit defender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 110, 110, 1000, 1);
                double attackerStartHealth = attacker.getHealth();
                double defenderStartHealth = defender.getHealth();
                final int exchanges = 20;
                try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                        final int[] counter = {0};
                        Runnable task = () -> {
                                attacker.attack(defender);
                                defender.attack(attacker);
                                counter[0]++;
                                if (counter[0] >= exchanges) {
                                        scheduler.shutdown();
                                }
                        };
                        scheduler.scheduleAtFixedRate(task, 0, 200, TimeUnit.MILLISECONDS);
                        try {
                                scheduler.awaitTermination(exchanges * 250L, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException _) {
                                Thread.currentThread().interrupt();
                        }
                }
                require(attackerStartHealth - attacker.getHealth() >= 6.0,
                                "Expected melee combat to inflict noticeable damage over a short exchange.");
                require(defenderStartHealth - defender.getHealth() >= 6.0,
                                "Expected melee combat to inflict noticeable damage over a short exchange.");
        }

        private static void verifyDamageParticleEffectTypes() {
                Unit meleeAttacker = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 1);
                Unit meleeDefender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC,
                                SoldierType.GALLIC_INFANTRY, 110, 110, 1000, 1);
                double meleeStartHealth = meleeDefender.getHealth();
                meleeAttacker.attack(meleeDefender);
                require(meleeDefender.getHealth() < meleeStartHealth,
                                "Expected a direct melee attack to inflict immediate damage for particle testing.");
                require(meleeDefender.getLastDamageEffectType() == Unit.DamageEffectType.MELEE,
                                "Expected direct-contact damage to use the melee particle effect.");
                Unit rangedAttacker = new Unit(null, SoldierClass.ARCHER, SoldierCulture.PARTHIAN,
                                SoldierType.PARTHIAN_ARCHER, 100, 100, 1000, 1);
                Unit rangedDefender = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 220, 100, 1000, 1);
                double rangedStartHealth = rangedDefender.getHealth();
                UnitManager.clearProjectilesForTesting();
                rangedAttacker.attack(rangedDefender);
                for (int step = 0; step < 48 && rangedDefender.getHealth() >= rangedStartHealth; step++) {
                        UnitManager.runProjectileTickForTesting();
                }
                require(rangedDefender.getHealth() < rangedStartHealth,
                                "Expected a projectile attack to land during particle effect testing.");
                require(rangedDefender.getLastDamageEffectType() == Unit.DamageEffectType.RANGED,
                                "Expected projectile damage to use the ranged particle effect.");
                UnitManager.clearProjectilesForTesting();
        }

        private static void verifyCounterRelationships() {
                Unit infantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 3);
                Unit cavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.GALLIC, SoldierType.GALLIC_CAVALRY,
                                120, 100, 1000, 3);
                Unit archer = new Unit(null, SoldierClass.ARCHER, SoldierCulture.PARTHIAN, SoldierType.PARTHIAN_ARCHER,
                                140, 100, 1000, 3);
                double infantryIntoCavalry = CombatSystem.calculateDamage(infantry, cavalry);
                double cavalryIntoInfantry = CombatSystem.calculateDamage(cavalry, infantry);
                double cavalryIntoArcher = CombatSystem.calculateDamage(cavalry, archer);
                double archerIntoCavalry = CombatSystem.calculateDamage(archer, cavalry);
                double archerIntoInfantry = CombatSystem.calculateDamage(archer, infantry);
                double infantryIntoArcher = CombatSystem.calculateDamage(infantry, archer);
                require(infantryIntoCavalry > cavalryIntoInfantry,
                                "Expected infantry to counter cavalry, but cavalry damage was higher.");
                require(cavalryIntoArcher > archerIntoCavalry,
                                "Expected cavalry to counter archers, but archer damage was higher.");
                require(archerIntoInfantry > infantryIntoArcher,
                                "Expected archers to counter infantry, but infantry damage was higher.");
        }

        private static void verifyArmorMitigation() {
                Unit cavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.GALLIC, SoldierType.GALLIC_CAVALRY,
                                100, 100, 1000, 3);
                Unit archer = new Unit(null, SoldierClass.ARCHER, SoldierCulture.PARTHIAN, SoldierType.PARTHIAN_ARCHER,
                                120, 100, 1000, 3);
                Unit heavyInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 140, 100, 1000, 3);
                Unit lightInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_LIGHT_INFANTRY, 160, 100, 1000, 3);
                double cavalryIntoHeavy = CombatSystem.calculateDamage(cavalry, heavyInfantry);
                double cavalryIntoLight = CombatSystem.calculateDamage(cavalry, lightInfantry);
                double archerIntoHeavy = CombatSystem.calculateDamage(archer, heavyInfantry);
                double archerIntoLight = CombatSystem.calculateDamage(archer, lightInfantry);
                require(cavalryIntoHeavy < cavalryIntoLight,
                                "Expected heavy armor to reduce cavalry charge damage relative to light infantry.");
                require(archerIntoHeavy < archerIntoLight,
                                "Expected heavy armor to reduce archer damage relative to light infantry.");
        }

        private static void verifyRomanHeavyInfantryModes() {
                Unit heavyInfantry = new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN,
                                SoldierType.ROMAN_HEAVY_INFANTRY, 100, 100, 1000, 3);
                Unit cavalry = new Unit(null, SoldierClass.CAVALRY, SoldierCulture.GALLIC, SoldierType.GALLIC_CAVALRY,
                                140, 100, 1000, 3);
                double swordRange = heavyInfantry.getAttackRange();
                double swordVsCavalry = CombatSystem.calculateDamage(heavyInfantry, cavalry);
                heavyInfantry.cycleWeaponMode();
                require(heavyInfantry.isRomanHeavyInfantryRangedJavelinMode(),
                                "Expected first Roman Heavy Infantry toggle to enter ranged javelin mode.");
                require(heavyInfantry.getAttackRange() > swordRange,
                                "Expected ranged javelin mode to extend Roman Heavy Infantry attack range.");
                double rangedVsCavalry = CombatSystem.calculateDamage(heavyInfantry, cavalry);
                heavyInfantry.cycleWeaponMode();
                require(heavyInfantry.isRomanHeavyInfantryMeleeJavelinMode(),
                                "Expected second Roman Heavy Infantry toggle to enter melee javelin mode.");
                double meleeJavelinVsCavalry = CombatSystem.calculateDamage(heavyInfantry, cavalry);
                require(meleeJavelinVsCavalry > swordVsCavalry,
                                "Expected melee javelin mode to outperform sword mode against cavalry.");
                require(meleeJavelinVsCavalry > rangedVsCavalry,
                                "Expected melee javelin mode to be the strongest Roman Heavy Infantry option against cavalry.");
        }

        private static void require(boolean condition, String message) {
                if (!condition) {
                        throw new IllegalStateException(message);
                }
        }
}
