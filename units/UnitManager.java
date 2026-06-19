package units;

import ai.AIController;
import campaign.CampaignManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import main.GamePanel;
import performance.GridPartition;
import units.Unit.SoldierClass;
import units.Unit.SoldierCulture;
import units.Unit.SoldierType;
import world.TileMap;

public class UnitManager {
    private static final Object UNITS_LOCK = new Object();
    private static final Object PROJECTILES_LOCK = new Object();
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger
            .getLogger(UnitManager.class.getName());
    private static final long SUPPLY_TICK_MS = 5000L;
    private static final long DAMAGE_PARTICLE_DURATION_MS = 700L;
    private static final int PARTITION_CELL_SIZE = GamePanel.TILE_SIZE * 2;
    private static final int UPDATE_NEARBY_RADIUS = GamePanel.TILE_SIZE * 8;
    private static final int MIN_PARALLEL_UNITS = 4;
    private static final Stroke STROKE_MEDIUM = new BasicStroke(2f);
    private static final Stroke STROKE_THICK = new BasicStroke(4f);
    private static final Color EXP_BAR_COLOR = new Color(200, 200, 0);
    private static final Color FORAGE_PARTICLE_OUTLINE = new Color(48, 54, 28, 120);
    private static final Color[] FORAGE_PARTICLE_COLORS = {new Color(126, 170, 72, 190), new Color(202, 184, 88, 175),
            new Color(150, 186, 83, 200), new Color(209, 192, 102, 170), new Color(112, 160, 70, 190),
            new Color(186, 170, 76, 165)};
    private static final Color SUPPLY_INDICATOR_OK = new Color(48, 168, 79);
    private static final Color SUPPLY_INDICATOR_BAD = new Color(185, 52, 52);
    private final GamePanel gp;
    private static final List<Unit> units = new ArrayList<>();
    private static final List<Projectile> projectiles = new ArrayList<>();
    private static TileMap activeMap;
    private final List<AIController> aiControllers;
    private final GridPartition partition;
    private final ExecutorService simulationExecutor;
    private final Object foodReservesLock = new Object();
    private final Map<SoldierCulture, Integer> foodReserves = new EnumMap<>(SoldierCulture.class);
    private SoldierCulture playerCulture;
    private long lastSupplyTickMs;

    public UnitManager(GamePanel gp) { this(gp, null); }

    public UnitManager(GamePanel gp, CampaignManager.ScenarioDefinition scenarioDefinition) {
        this.gp = gp;
        synchronized (UNITS_LOCK) {
            units.clear();
        }
        synchronized (PROJECTILES_LOCK) {
            projectiles.clear();
        }
        initializeUnits(scenarioDefinition);
        initializeFoodReserves(scenarioDefinition);
        aiControllers = initializeAiControllers(scenarioDefinition);
        TileMap map = gp.getTileManager().getTileMap();
        setActiveMap(map);
        int cols = (map != null)
                ? Math.max(1, (int)Math.ceil(map.getWidth() * GamePanel.TILE_SIZE / (double)PARTITION_CELL_SIZE))
                : 32;
        int rows = (map != null)
                ? Math.max(1, (int)Math.ceil(map.getHeight() * GamePanel.TILE_SIZE / (double)PARTITION_CELL_SIZE))
                : 24;
        partition = new GridPartition(cols, rows, PARTITION_CELL_SIZE);
        int workerThreads = resolveWorkerThreads();
        simulationExecutor = workerThreads > 1
                ? Executors.newFixedThreadPool(workerThreads, new SimulationThreadFactory())
                : null;
        lastSupplyTickMs = System.currentTimeMillis();
    }

    private void initializeUnits(CampaignManager.ScenarioDefinition scenarioDefinition) {
        playerCulture = SoldierCulture.ROMAN;
        if (scenarioDefinition == null || scenarioDefinition.getForces().isEmpty()) {
            addDefaultUnits();
            return;
        }
        for (CampaignManager.ForcePlacement forcePlacement : scenarioDefinition.getForces()) {
            spawnForcePlacement(forcePlacement);
        }
    }

    private void spawnForcePlacement(CampaignManager.ForcePlacement forcePlacement) {
        if (forcePlacement == null) {
            return;
        }
        SoldierType soldierType = forcePlacement.getType();
        SoldierClass soldierClass = inferClass(soldierType);
        SoldierCulture culture = forcePlacement.getCulture();
        int requestedSize = forcePlacement.getSize();
        int experienceLevel = forcePlacement.getExperienceLevel();
        List<Integer> unitSizes = decomposeUnitSize(requestedSize);
        for (int index = 0; index < unitSizes.size(); index++) {
            int size = unitSizes.get(index);
            int[] offsets = resolveSpawnOffset(index);
            addUnit(new Unit(null, soldierClass, culture, soldierType, forcePlacement.getX() + offsets[0],
                    forcePlacement.getY() + offsets[1], size, experienceLevel));
        }
    }

    private static final int[] ALLOWED_UNIT_SIZES_DESC = {5000, 2000, 1000, 500, 100};

    private List<Integer> decomposeUnitSize(int requestedSize) {
        int remaining = Math.max(ALLOWED_UNIT_SIZES_DESC[ALLOWED_UNIT_SIZES_DESC.length - 1], requestedSize);
        List<Integer> sizes = new ArrayList<>();
        for (int allowed : ALLOWED_UNIT_SIZES_DESC) {
            while (remaining >= allowed) {
                sizes.add(allowed);
                remaining -= allowed;
            }
        }
        if (remaining > 0) {
            sizes.add(ALLOWED_UNIT_SIZES_DESC[ALLOWED_UNIT_SIZES_DESC.length - 1]);
        }
        return sizes;
    }

    private int[] resolveSpawnOffset(int index) {
        // Spread decomposed formations into a compact grid so they don't all
        // overlap at the same spawn point.
        int spacing = (int)Math.round(GamePanel.TILE_SIZE * 0.9);
        int columns = 3;
        int row = index / columns;
        int col = index % columns;
        int offsetX = (col - 1) * spacing;
        int offsetY = row * spacing;
        return new int[] {offsetX, offsetY};
    }

    private void addDefaultUnits() {
        addUnit(new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_HEAVY_INFANTRY, 300, 200,
                5000, 1));
        addUnit(new Unit(null, SoldierClass.INFANTRY, SoldierCulture.ROMAN, SoldierType.ROMAN_LIGHT_INFANTRY, 300, 300,
                2000, 1));
        addUnit(new Unit(null, SoldierClass.ARCHER, SoldierCulture.ROMAN, SoldierType.ROMAN_ARCHER, 350, 300, 1000, 1));
        addUnit(new Unit(null, SoldierClass.CAVALRY, SoldierCulture.ROMAN, SoldierType.ROMAN_CAVALRY, 400, 400, 2000,
                3));
        addUnit(new Unit(null, SoldierClass.INFANTRY, SoldierCulture.GALLIC, SoldierType.GALLIC_INFANTRY, 50, 50, 5000,
                1));
        addUnit(new Unit(null, SoldierClass.CAVALRY, SoldierCulture.GALLIC, SoldierType.GALLIC_CAVALRY, 200, 100, 5000,
                1));
        addUnit(new Unit(null, SoldierClass.ARCHER, SoldierCulture.GALLIC, SoldierType.GALLIC_ARCHER, 300, 100, 1000,
                1));
    }

    private void initializeFoodReserves(CampaignManager.ScenarioDefinition scenarioDefinition) {
        Map<SoldierCulture, Integer> manpowerByCulture = new EnumMap<>(SoldierCulture.class);
        Map<SoldierCulture, Integer> scenarioStartingFood = scenarioDefinition != null
                ? scenarioDefinition.getStartingFood()
                : Map.of();
        for (Unit unit : getUnitsSnapshot()) {
            if (unit.getHealth() <= 0 || unit.hasSurrendered()) {
                continue;
            }
            manpowerByCulture.merge(unit.getSoldierCulture(), unit.getUnitSize(), Integer::sum);
        }
        synchronized (foodReservesLock) {
            foodReserves.clear();
            for (Map.Entry<SoldierCulture, Integer> entry : manpowerByCulture.entrySet()) {
                int startingFoodAmount = scenarioStartingFood.containsKey(entry.getKey())
                        ? Math.max(0, scenarioStartingFood.get(entry.getKey()))
                        : estimateDefaultStartingFood(entry.getValue());
                foodReserves.put(entry.getKey(), startingFoodAmount);
            }
        }
    }

    private int estimateDefaultStartingFood(int manpower) { return Math.max(1200, (int)Math.round(manpower * 0.16)); }

    private SoldierClass inferClass(SoldierType soldierType) {
        return switch (soldierType) {
        case ROMAN_ARCHER, GALLIC_ARCHER, PARTHIAN_ARCHER, EGYPTIAN_ARCHER -> SoldierClass.ARCHER;
        case PARTHIAN_HORSE_ARCHER -> SoldierClass.HORSE_ARCHER;
        case ROMAN_CAVALRY, GALLIC_CAVALRY, PARTHIAN_CAVALRY, EGYPTIAN_CAVALRY, EGYPTIAN_CHARIOT -> SoldierClass.CAVALRY;
        default -> SoldierClass.INFANTRY;
        };
    }

    public GamePanel getGp() { return gp; }

    public void update() {
        updateProjectiles();
        Unit[] unitSnapshot = getUnitsSnapshot();
        partition.clear();
        for (Unit unit : unitSnapshot) {
            if (unit.getHealth() > 0 && !unit.hasSurrendered()) {
                partition.add(unit);
            }
        }
        List<Unit> defeatedUnits = updateUnits(unitSnapshot);
        if (defeatedUnits != null && !defeatedUnits.isEmpty()) {
            removeUnits(defeatedUnits);
        }
        Unit[] activeUnits = getUnitsSnapshot();
        updateSupplyState(activeUnits);
        updateAiControllers(activeUnits);
    }

    private List<Unit> updateUnits(Unit[] unitSnapshot) {
        if (simulationExecutor == null || unitSnapshot.length < MIN_PARALLEL_UNITS) {
            return updateUnitsSequentially(unitSnapshot);
        }
        List<Callable<Unit>> tasks = new ArrayList<>(unitSnapshot.length);
        for (Unit unit : unitSnapshot) {
            tasks.add(() -> {
                try {
                    return updateUnitForTick(unit);
                } catch (RuntimeException runtimeException) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Unit update failed", runtimeException);
                    return null;
                }
            });
        }
        List<Unit> defeatedUnits = null;
        try {
            List<Future<Unit>> futures = simulationExecutor.invokeAll(tasks);
            for (Future<Unit> future : futures) {
                Unit defeatedUnit = future.get();
                if (defeatedUnit != null) {
                    if (defeatedUnits == null) {
                        defeatedUnits = new ArrayList<>();
                    }
                    defeatedUnits.add(defeatedUnit);
                }
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException executionException) {
            LOGGER.log(java.util.logging.Level.WARNING, "Unit update worker failed", executionException);
        }
        return defeatedUnits;
    }

    private List<Unit> updateUnitsSequentially(Unit[] unitSnapshot) {
        List<Unit> defeatedUnits = null;
        for (Unit unit : unitSnapshot) {
            Unit defeatedUnit = updateUnitForTick(unit);
            if (defeatedUnit != null) {
                if (defeatedUnits == null) {
                    defeatedUnits = new ArrayList<>();
                }
                defeatedUnits.add(defeatedUnit);
            }
        }
        return defeatedUnits;
    }

    private Unit updateUnitForTick(Unit unit) {
        if (unit.getHealth() <= 0 || unit.hasSurrendered()) {
            return unit;
        }
        Unit[] nearbyUnits = getNearbyUnitsForUpdate(unit);
        unit.update(nearbyUnits);
        return unit.getHealth() <= 0 || unit.hasSurrendered() ? unit : null;
    }

    private void updateAiControllers(Unit[] activeUnits) {
        if (aiControllers.isEmpty()) {
            return;
        }
        if (simulationExecutor == null || aiControllers.size() == 1) {
            for (AIController aiController : aiControllers) {
                aiController.update(activeUnits);
            }
            return;
        }
        List<Callable<Void>> tasks = new ArrayList<>(aiControllers.size());
        for (AIController aiController : aiControllers) {
            tasks.add(() -> {
                try {
                    aiController.update(activeUnits);
                } catch (RuntimeException runtimeException) {
                    LOGGER.log(java.util.logging.Level.WARNING, "AI update failed", runtimeException);
                }
                return null;
            });
        }
        try {
            List<Future<Void>> futures = simulationExecutor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException executionException) {
            LOGGER.log(java.util.logging.Level.WARNING, "AI worker failed", executionException);
        }
    }

    private static int resolveWorkerThreads() {
        int availableProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int configuredThreads = Integer.getInteger("romanrts.workerThreads", -1);
        int desiredThreads = configuredThreads > 0 ? configuredThreads : Math.max(1, availableProcessors - 1);
        return Math.clamp(desiredThreads, 1, Math.max(1, availableProcessors));
    }

    public void shutdown() {
        if (simulationExecutor != null) {
            simulationExecutor.shutdownNow();
        }
    }

    private Unit[] getNearbyUnitsForUpdate(Unit unit) {
        if (unit == null) {
            return new Unit[0];
        }
        int radius = Math.max(UPDATE_NEARBY_RADIUS,
                (int)Math.ceil(unit.getAttackRange() + Math.max(unit.getFormationWidth(), unit.getFormationHeight())));
        List<Unit> nearbyUnits = partition.queryNearby(unit.getX(), unit.getY(), radius);
        if (!nearbyUnits.contains(unit)) {
            nearbyUnits.add(unit);
        }
        return nearbyUnits.toArray(Unit[]::new);
    }

    private void updateSupplyState(Unit[] unitSnapshot) {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastSupplyTickMs;
        if (elapsedMs < SUPPLY_TICK_MS) {
            return;
        }
        int elapsedTicks = (int)(elapsedMs / SUPPLY_TICK_MS);
        lastSupplyTickMs += elapsedTicks * SUPPLY_TICK_MS;
        for (int tick = 0; tick < elapsedTicks; tick++) {
            applySupplyTick(unitSnapshot);
        }
    }

    private void applySupplyTick(Unit[] unitSnapshot) {
        TileMap map = activeMap;
        for (Unit unit : unitSnapshot) {
            if (unit == null || unit.getHealth() <= 0 || unit.hasSurrendered()) {
                continue;
            }
            if (map != null) {
                unit.collectForage(map);
                addFoodReserve(unit.getSoldierCulture(), unit.transferExcessCarriedFoodToReserve());
            }
            int foodDemand = unit.getFoodConsumptionPerTick();
            foodDemand -= unit.consumeCarriedFood(foodDemand);
            if (foodDemand > 0) {
                foodDemand -= consumeFromReserve(unit.getSoldierCulture(), foodDemand);
            }
            unit.setSupplied(foodDemand <= 0);
        }
    }

    private int consumeFromReserve(SoldierCulture culture, int requestedAmount) {
        if (culture == null || requestedAmount <= 0) {
            return 0;
        }
        synchronized (foodReservesLock) {
            int availableFood = foodReserves.getOrDefault(culture, 0);
            int consumedFood = Math.min(requestedAmount, availableFood);
            if (consumedFood > 0) {
                foodReserves.put(culture, availableFood - consumedFood);
            }
            return consumedFood;
        }
    }

    private void addFoodReserve(SoldierCulture culture, int addedFood) {
        if (culture == null || addedFood <= 0) {
            return;
        }
        synchronized (foodReservesLock) {
            foodReserves.merge(culture, addedFood, Integer::sum);
        }
    }

    private List<AIController> initializeAiControllers(CampaignManager.ScenarioDefinition scenarioDefinition) {
        List<AIController> controllers = new ArrayList<>();
        Set<SoldierCulture> aiCultures = new LinkedHashSet<>();
        for (Unit unit : units) {
            if (unit.getSoldierCulture() != playerCulture) {
                aiCultures.add(unit.getSoldierCulture());
            }
        }
        if (aiCultures.isEmpty()) {
            aiCultures.add(SoldierCulture.GALLIC);
        }
        for (SoldierCulture culture : aiCultures) {
            CampaignManager.AIDirective directive = scenarioDefinition != null
                    ? scenarioDefinition.getAiDirective(culture)
                    : null;
            controllers.add(new AIController(this, culture, directive));
        }
        return controllers;
    }

    public void draw(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        Unit[] unitSnapshot = getUnitsSnapshot();
        Set<Unit> selectedTargets = new LinkedHashSet<>();
        long nowMs = System.currentTimeMillis();
        for (Unit unit : unitSnapshot) {
            Rectangle2D.Double bounds = unit.getBounds();
            Rectangle2D footprintBounds = unit.getFootprintBounds();
            if (visibleWorldBounds != null && !footprintBounds.intersects(visibleWorldBounds)) {
                continue;
            }
            if (unit.isSelected()) {
                Unit target = unit.getEngagementTarget();
                if (target != null && target.getHealth() > 0 && !target.hasSurrendered()) {
                    selectedTargets.add(target);
                }
            }
            drawUnit(g2d, unit, bounds);
            drawForagingParticles(g2d, unit, footprintBounds);
            drawDamageParticles(g2d, unit, footprintBounds, nowMs);
            int barWidth = (int)Math.max(GamePanel.TILE_SIZE, Math.round(footprintBounds.getWidth()));
            int expBarHeight = 3;
            int moraleBarHeight = 3;
            int healthBarHeight = 4;
            int spacing = 2;
            int x = (int)Math.round(footprintBounds.getCenterX() - barWidth * 0.5);
            int topY = (int)Math.round(footprintBounds.getY()) - spacing;
            int healthY = topY - healthBarHeight;
            int moraleY = healthY - spacing - moraleBarHeight;
            int expY = moraleY - spacing - expBarHeight;
            double healthPct = Math.clamp(unit.getHealth() / Math.max(1.0, unit.getMaxHealth()), 0.0, 1.0);
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x, healthY, barWidth, healthBarHeight);
            g2d.setColor(Color.GREEN);
            g2d.fillRect(x, healthY, (int)(barWidth * healthPct), healthBarHeight);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, healthY, barWidth, healthBarHeight);
            double moralePct = Math.clamp(unit.getMorale() / 100.0, 0.0, 1.0);
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x, moraleY, barWidth, moraleBarHeight);
            g2d.setColor(Color.BLUE);
            g2d.fillRect(x, moraleY, (int)(barWidth * moralePct), moraleBarHeight);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, moraleY, barWidth, moraleBarHeight);
            double expBase = unit.getExperienceLevelNum() * 100.0;
            double expToNext = 100.0;
            double expProgress = Math.clamp((unit.getExperiencePoints() - expBase) / expToNext, 0.0, 1.0);
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x, expY, barWidth, expBarHeight);
            g2d.setColor(EXP_BAR_COLOR);
            g2d.fillRect(x, expY, (int)(barWidth * expProgress), expBarHeight);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x, expY, barWidth, expBarHeight);
            drawSupplyIndicator(g2d, unit, x + barWidth + 4, expY - 2);
        }
        drawSelectedTargetIndicators(g2d, selectedTargets, visibleWorldBounds);
        drawProjectiles(g2d, visibleWorldBounds);
    }

    private void drawForagingParticles(Graphics2D g2d, Unit unit, Rectangle2D footprintBounds) {
        if (!unit.isForaging() || unit.isMoving() || unit.hasSurrendered() || unit.getHealth() <= 0) {
            return;
        }
        double left = footprintBounds.getX();
        double right = footprintBounds.getMaxX();
        double top = footprintBounds.getY();
        double bottom = footprintBounds.getMaxY();
        double centerX = footprintBounds.getCenterX();
        double centerY = footprintBounds.getCenterY();
        drawForageParticle(g2d, left - 5.0, centerY - 6.0, 4, FORAGE_PARTICLE_COLORS[0]);
        drawForageParticle(g2d, left + 3.0, top + 4.0, 3, FORAGE_PARTICLE_COLORS[1]);
        drawForageParticle(g2d, centerX - 4.0, top + 2.0, 4, FORAGE_PARTICLE_COLORS[2]);
        drawForageParticle(g2d, right - 2.0, centerY - 3.0, 3, FORAGE_PARTICLE_COLORS[3]);
        drawForageParticle(g2d, centerX + 6.0, bottom - 8.0, 4, FORAGE_PARTICLE_COLORS[4]);
        drawForageParticle(g2d, left + 1.0, bottom - 6.0, 3, FORAGE_PARTICLE_COLORS[5]);
    }

    private void drawForageParticle(Graphics2D g2d, double particleX, double particleY, int diameter, Color fillColor) {
        int drawX = (int)Math.round(particleX - diameter * 0.5);
        int drawY = (int)Math.round(particleY - diameter * 0.5);
        g2d.setColor(fillColor);
        g2d.fillOval(drawX, drawY, diameter, diameter);
        g2d.setColor(FORAGE_PARTICLE_OUTLINE);
        g2d.drawOval(drawX, drawY, diameter, diameter);
    }

    private void drawDamageParticles(Graphics2D g2d, Unit unit, Rectangle2D footprintBounds, long nowMs) {
        long damageAgeMs = nowMs - unit.getLastDamageTimeMs();
        if (unit.hasSurrendered() || unit.getHealth() <= 0 || damageAgeMs < 0
                || damageAgeMs > DAMAGE_PARTICLE_DURATION_MS) {
            return;
        }
        double progress = Math.clamp(damageAgeMs / (double)DAMAGE_PARTICLE_DURATION_MS, 0.0, 1.0);
        double fade = 1.0 - progress;
        double centerX = footprintBounds.getCenterX();
        double centerY = footprintBounds.getCenterY();
        int ovalWidth = (int)Math.round(Math.max(12.0, footprintBounds.getWidth() * (0.34 + progress * 0.10)));
        int ovalHeight = (int)Math.round(Math.max(10.0, footprintBounds.getHeight() * (0.24 + progress * 0.08)));
        int drawX = (int)Math.round(centerX - ovalWidth * 0.5);
        int drawY = (int)Math.round(centerY - ovalHeight * 0.5 - progress * 2.0);
        int fillAlpha = Math.max(0, (int)Math.round(70.0 * fade));
        int outlineAlpha = Math.max(0, (int)Math.round(200.0 * fade));
        g2d.setColor(new Color(255, 232, 120, fillAlpha));
        g2d.fillOval(drawX, drawY, ovalWidth, ovalHeight);
        g2d.setColor(new Color(255, 220, 76, outlineAlpha));
        g2d.drawOval(drawX, drawY, ovalWidth, ovalHeight);
    }

    private void drawSelectedTargetIndicators(Graphics2D g2d, Set<Unit> selectedTargets,
            Rectangle2D visibleWorldBounds) {
        for (Unit target : selectedTargets) {
            if (target == null || target.getHealth() <= 0 || target.hasSurrendered()) {
                continue;
            }
            Rectangle2D targetBounds = target.getFootprintBounds();
            if (visibleWorldBounds != null && !targetBounds.intersects(visibleWorldBounds)) {
                continue;
            }
            drawTargetOutline(g2d, target);
        }
    }

    private void drawTargetOutline(Graphics2D g2d, Unit unit) {
        Rectangle2D.Double bounds = unit.getBounds();
        AffineTransform previousTransform = g2d.getTransform();
        Stroke previousStroke = g2d.getStroke();
        g2d.translate(bounds.getCenterX(), bounds.getCenterY());
        g2d.rotate(unit.getRenderAngleRadians());
        Rectangle2D.Double localBounds = new Rectangle2D.Double(-bounds.width * 0.5, -bounds.height * 0.5, bounds.width,
                bounds.height);
        g2d.setColor(new Color(134, 12, 12, 235));
        g2d.setStroke(STROKE_THICK);
        g2d.draw(localBounds);
        g2d.setColor(new Color(255, 92, 92, 220));
        g2d.setStroke(STROKE_MEDIUM);
        g2d.draw(localBounds);
        g2d.setStroke(previousStroke);
        g2d.setTransform(previousTransform);
    }

    private void drawSupplyIndicator(Graphics2D g2d, Unit unit, int x, int y) {
        int indicatorSize = 9;
        Color indicatorColor = unit.isSupplied() ? SUPPLY_INDICATOR_OK : SUPPLY_INDICATOR_BAD;
        g2d.setColor(indicatorColor);
        g2d.fillOval(x, y, indicatorSize, indicatorSize);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(x, y, indicatorSize, indicatorSize);
        if (!unit.isSupplied()) {
            g2d.drawLine(x + 2, y + 2, x + indicatorSize - 2, y + indicatorSize - 2);
        }
    }

    private void drawUnit(Graphics2D g2d, Unit unit, Rectangle2D.Double bounds) {
        AffineTransform previousTransform = g2d.getTransform();
        Stroke previousStroke = g2d.getStroke();
        g2d.translate(bounds.getCenterX(), bounds.getCenterY());
        g2d.rotate(unit.getRenderAngleRadians());
        Rectangle2D.Double localBounds = new Rectangle2D.Double(-bounds.width * 0.5, -bounds.height * 0.5, bounds.width,
                bounds.height);
        g2d.setColor(unit.getColor());
        g2d.fill(localBounds);
        drawDirectionIndicator(g2d, localBounds);
        drawNatoClassSymbol(g2d, unit, localBounds);
        g2d.setStroke(previousStroke);
        g2d.setColor(Color.BLACK);
        g2d.draw(localBounds);
        if (unit.isSelected()) {
            g2d.setColor(Color.GREEN);
            g2d.setStroke(STROKE_MEDIUM);
            g2d.draw(localBounds);
        }
        g2d.setStroke(previousStroke);
        g2d.setTransform(previousTransform);
    }

    private void drawDirectionIndicator(Graphics2D g2d, Rectangle2D.Double bounds) {
        int centerX = (int)Math.round(bounds.getCenterX());
        int tipY = (int)Math.round(bounds.y + Math.max(4.0, bounds.height * 0.12));
        int baseY = tipY + Math.max(6, (int)Math.round(bounds.height * 0.18));
        int halfWidth = Math.max(4, (int)Math.round(bounds.width * 0.14));
        int[] indicatorX = {centerX, centerX - halfWidth, centerX + halfWidth};
        int[] indicatorY = {tipY, baseY, baseY};
        g2d.setColor(new Color(245, 236, 198, 220));
        g2d.fillPolygon(indicatorX, indicatorY, indicatorX.length);
        g2d.setColor(new Color(30, 24, 18));
        g2d.drawPolygon(indicatorX, indicatorY, indicatorX.length);
    }

    private void drawNatoClassSymbol(Graphics2D g2d, Unit unit, Rectangle2D.Double bounds) {
        int left = (int)Math.round(bounds.x + Math.max(2.0, bounds.width * 0.08));
        int right = (int)Math.round(bounds.x + bounds.width - Math.max(2.0, bounds.width * 0.08));
        int top = (int)Math.round(bounds.y + Math.max(2.0, bounds.height * 0.08));
        int bottom = (int)Math.round(bounds.y + bounds.height - Math.max(2.0, bounds.height * 0.08));
        int centerY = (int)Math.round(bounds.getCenterY());
        int bowWidth = Math.max(6, (right - left) / 3);
        Stroke previousStroke = g2d.getStroke();
        g2d.setColor(Color.BLACK);
        g2d.setStroke(STROKE_MEDIUM);
        switch (unit.getSoldierClass()) {
        case INFANTRY -> {
            g2d.drawLine(left, top, right, bottom);
            g2d.drawLine(right, top, left, bottom);
        }
        case CAVALRY -> g2d.drawLine(left, bottom, right, top);
        case ARCHER -> {
            g2d.drawLine(left, centerY, right - 4, centerY);
            g2d.drawLine(right - 4, centerY, right - 10, centerY - 4);
            g2d.drawLine(right - 4, centerY, right - 10, centerY + 4);
            g2d.drawArc(left, top, bowWidth, Math.max(8, bottom - top), -90, 180);
        }
        case HORSE_ARCHER -> {
            g2d.drawLine(left, bottom, right, top);
            g2d.drawLine(left, centerY, right - 4, centerY);
            g2d.drawLine(right - 4, centerY, right - 10, centerY - 4);
            g2d.drawLine(right - 4, centerY, right - 10, centerY + 4);
        }
        }
        g2d.setStroke(previousStroke);
    }

    private static void updateProjectiles() {
        synchronized (PROJECTILES_LOCK) {
            Iterator<Projectile> iterator = projectiles.iterator();
            while (iterator.hasNext()) {
                Projectile projectile = iterator.next();
                projectile.update();
                if (projectile.shouldResolveImpact()) {
                    projectile.resolveImpact();
                    iterator.remove();
                    continue;
                }
                if (projectile.isExpired()) {
                    iterator.remove();
                }
            }
        }
    }

    private void drawProjectiles(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        for (Projectile projectile : getProjectilesSnapshot()) {
            if (visibleWorldBounds != null && !projectile.intersects(visibleWorldBounds)) {
                continue;
            }
            projectile.draw(g2d);
        }
    }

    public List<Unit> getSelectedUnits() {
        List<Unit> selectedUnits = new ArrayList<>();
        for (Unit unit : getUnitsSnapshot()) {
            if (unit.isSelected()) {
                selectedUnits.add(unit);
            }
        }
        return selectedUnits;
    }

    public void cycleSelectedHeavyInfantryModes() {
        for (Unit unit : getUnitsSnapshot()) {
            if (unit.isSelected() && unit.supportsWeaponModeToggle()) {
                unit.cycleWeaponMode();
            }
        }
    }

    public void toggleSelectedForaging() {
        List<Unit> selectedUnits = getSelectedUnits();
        if (selectedUnits.isEmpty()) {
            return;
        }
        boolean enableForaging = false;
        for (Unit unit : selectedUnits) {
            if (!unit.isForaging()) {
                enableForaging = true;
                break;
            }
        }
        for (Unit unit : selectedUnits) {
            unit.setForaging(enableForaging);
        }
    }

    public int getSelectedForagingCount() {
        int foragingCount = 0;
        for (Unit unit : getUnitsSnapshot()) {
            if (unit.isSelected() && unit.isForaging()) {
                foragingCount++;
            }
        }
        return foragingCount;
    }

    public int getFoodReserve(SoldierCulture culture) {
        if (culture == null) {
            return 0;
        }
        synchronized (foodReservesLock) {
            return foodReserves.getOrDefault(culture, 0);
        }
    }

    public int getPlayerFoodReserve() { return getFoodReserve(playerCulture); }

    public int getForagingCount(SoldierCulture culture) {
        int foragingCount = 0;
        for (Unit unit : getUnitsSnapshot()) {
            if (unit.getSoldierCulture() == culture && unit.isForaging() && !unit.hasSurrendered()
                    && unit.getHealth() > 0) {
                foragingCount++;
            }
        }
        return foragingCount;
    }

    public void runSupplyTickForTesting() { applySupplyTick(getUnitsSnapshot()); }

    public static void runProjectileTickForTesting() { updateProjectiles(); }

    public static void clearProjectilesForTesting() {
        synchronized (PROJECTILES_LOCK) {
            projectiles.clear();
        }
    }

    public static void spawnProjectile(Unit attacker, Unit target, double damage) {
        if (attacker == null || target == null || damage <= 0) {
            return;
        }
        double dx = target.getX() * 1.0 - attacker.getX();
        double dy = target.getY() * 1.0 - attacker.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1e-6) {
            return;
        }
        ProjectileStyle style = getProjectileStyle(attacker);
        double projectileSpeed = getProjectileSpeed(style);
        double sourceX = attacker.getX()
                + attacker.getFacingVectorX() * Math.max(8.0, attacker.getFormationWidth() * 0.45);
        double sourceY = attacker.getY()
                + attacker.getFacingVectorY() * Math.max(8.0, attacker.getFormationHeight() * 0.45);
        double velocityX = dx / length * projectileSpeed;
        double velocityY = dy / length * projectileSpeed;
        int lifetimeTicks = Math.max(6, (int)Math.ceil(length / projectileSpeed));
        synchronized (PROJECTILES_LOCK) {
            projectiles.add(new Projectile(attacker, target, style, damage, sourceX, sourceY, velocityX, velocityY,
                    lifetimeTicks));
        }
    }

    public static void clearUnitsForTesting() {
        synchronized (UNITS_LOCK) {
            units.clear();
        }
    }

    public static void addUnitForTesting(Unit unit) {
        if (unit == null) {
            return;
        }
        synchronized (UNITS_LOCK) {
            units.add(unit);
        }
    }

    public static void addUnitsForTesting(List<Unit> addedUnits) {
        if (addedUnits == null || addedUnits.isEmpty()) {
            return;
        }
        synchronized (UNITS_LOCK) {
            units.addAll(addedUnits);
        }
    }

    public static Unit[] getUnitsSnapshot() {
        synchronized (UNITS_LOCK) {
            return units.toArray(Unit[]::new);
        }
    }

    private static Projectile[] getProjectilesSnapshot() {
        synchronized (PROJECTILES_LOCK) {
            return projectiles.toArray(Projectile[]::new);
        }
    }

    private static void addUnit(Unit unit) {
        synchronized (UNITS_LOCK) {
            units.add(unit);
        }
    }

    private static void removeUnits(List<Unit> defeatedUnits) {
        synchronized (UNITS_LOCK) {
            units.removeAll(defeatedUnits);
        }
    }

    private static ProjectileStyle getProjectileStyle(Unit attacker) {
        return switch (attacker.getSoldierType()) {
        case EGYPTIAN_CHARIOT -> ProjectileStyle.JAVELIN;
        case ROMAN_HEAVY_INFANTRY -> attacker.isRomanHeavyInfantryRangedJavelinMode() ? ProjectileStyle.JAVELIN
                : ProjectileStyle.ARROW;
        case PARTHIAN_ARCHER, PARTHIAN_HORSE_ARCHER -> ProjectileStyle.HEAVY_ARROW;
        default -> ProjectileStyle.ARROW;
        };
    }

    private static double getProjectileSpeed(ProjectileStyle style) {
        return switch (style) {
        case ARROW -> 14.0;
        case HEAVY_ARROW -> 12.5;
        case JAVELIN -> 10.0;
        };
    }

    private enum ProjectileStyle {
        ARROW, HEAVY_ARROW, JAVELIN
    }

    private static final class SimulationThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SIM WORKER " + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Projectile {
        private final Unit attacker;
        private final Unit target;
        private final ProjectileStyle style;
        private final double damage;
        private double x;
        private double y;
        private final double velocityX;
        private final double velocityY;
        private int ticksRemaining;

        private Projectile(Unit attacker, Unit target, ProjectileStyle style, double damage, double x, double y,
                double velocityX, double velocityY, int ticksRemaining) {
            this.attacker = attacker;
            this.target = target;
            this.style = style;
            this.damage = damage;
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.ticksRemaining = ticksRemaining;
        }

        private void update() {
            x += velocityX;
            y += velocityY;
            ticksRemaining--;
        }

        private boolean shouldResolveImpact() {
            if (target == null || target.hasSurrendered() || target.getHealth() <= 0) {
                return false;
            }
            double impactRadius = Math.max(6.0,
                    Math.max(target.getFormationWidth(), target.getFormationHeight()) * 0.45);
            double distanceToTarget = Math.hypot(target.getX() - x, target.getY() - y);
            return distanceToTarget <= impactRadius;
        }

        private void resolveImpact() {
            if (target == null || target.hasSurrendered() || target.getHealth() <= 0) {
                return;
            }
            target.receiveDamage(damage, attacker, Unit.DamageEffectType.RANGED);
            if (attacker != null && !attacker.hasSurrendered() && attacker.getHealth() > 0) {
                attacker.gainExperience(damage * 0.1);
            }
        }

        private boolean isExpired() {
            return ticksRemaining <= 0 || target == null || target.hasSurrendered() || target.getHealth() <= 0;
        }

        private boolean intersects(Rectangle2D visibleWorldBounds) {
            if (visibleWorldBounds == null) {
                return true;
            }
            double padding = switch (style) {
            case ARROW -> 6.0;
            case HEAVY_ARROW -> 7.5;
            case JAVELIN -> 10.0;
            };
            return visibleWorldBounds.intersects(x - padding, y - padding, padding * 2.0, padding * 2.0);
        }

        private void draw(Graphics2D g2d) {
            double angle = Math.atan2(velocityY, velocityX);
            double shaftLength = switch (style) {
            case ARROW -> 10.0;
            case HEAVY_ARROW -> 12.0;
            case JAVELIN -> 16.0;
            };
            double shaftThickness = switch (style) {
            case ARROW -> 2.0;
            case HEAVY_ARROW -> 3.0;
            case JAVELIN -> 4.0;
            };
            double tailX = x - Math.cos(angle) * shaftLength;
            double tailY = y - Math.sin(angle) * shaftLength;
            int tipX = (int)Math.round(x);
            int tipY = (int)Math.round(y);
            int tailXi = (int)Math.round(tailX);
            int tailYi = (int)Math.round(tailY);
            g2d.setColor(style == ProjectileStyle.JAVELIN ? new Color(120, 88, 54) : new Color(65, 35, 15));
            g2d.setStroke(new BasicStroke((float)shaftThickness));
            g2d.drawLine(tailXi, tailYi, tipX, tipY);
            if (style == ProjectileStyle.JAVELIN) {
                int headLeftX = (int)Math.round(x - Math.cos(angle) * 5 + Math.cos(angle + Math.PI * 0.5) * 3);
                int headLeftY = (int)Math.round(y - Math.sin(angle) * 5 + Math.sin(angle + Math.PI * 0.5) * 3);
                int headRightX = (int)Math.round(x - Math.cos(angle) * 5 + Math.cos(angle - Math.PI * 0.5) * 3);
                int headRightY = (int)Math.round(y - Math.sin(angle) * 5 + Math.sin(angle - Math.PI * 0.5) * 3);
                g2d.setColor(Color.DARK_GRAY);
                g2d.drawLine(headLeftX, headLeftY, tipX, tipY);
                g2d.drawLine(headRightX, headRightY, tipX, tipY);
            } else {
                double featherSpread = style == ProjectileStyle.HEAVY_ARROW ? 4.0 : 3.0;
                double featherBack = style == ProjectileStyle.HEAVY_ARROW ? 5.0 : 4.0;
                int leftFeatherX = (int)Math.round(tailX + Math.cos(angle + Math.PI * 0.75) * featherBack);
                int leftFeatherY = (int)Math
                        .round(tailY + Math.sin(angle + Math.PI * 0.75) * featherBack + featherSpread);
                int rightFeatherX = (int)Math.round(tailX + Math.cos(angle - Math.PI * 0.75) * featherBack);
                int rightFeatherY = (int)Math
                        .round(tailY + Math.sin(angle - Math.PI * 0.75) * featherBack - featherSpread);
                g2d.drawLine(tailXi, tailYi, leftFeatherX, leftFeatherY);
                g2d.drawLine(tailXi, tailYi, rightFeatherX, rightFeatherY);
                g2d.setColor(style == ProjectileStyle.HEAVY_ARROW ? Color.GRAY : Color.DARK_GRAY);
                g2d.fillOval(tipX - 1, tipY - 1, 3, 3);
            }
            g2d.setStroke(new BasicStroke(1f));
        }
    }

    public SoldierCulture getPlayerCulture() { return playerCulture; }

    public void setPlayerCulture(SoldierCulture playerCulture) { this.playerCulture = playerCulture; }

    public static TileMap getActiveMap() { return activeMap; }

    public static void setActiveMap(TileMap map) { activeMap = map; }
}
