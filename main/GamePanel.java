package main;

import camera.RTSCamera;
import campaign.CampaignManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import units.Unit;
import units.UnitManager;
import utils.KeyHandler;
import utils.MouseHandler;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.TileManager;
import world.TileMap;

public class GamePanel extends JPanel implements Runnable {
    private static final int ORIG_TILE_SIZE = 16;
    private static final int SCALE = 3;
    public static final int TILE_SIZE = ORIG_TILE_SIZE * SCALE;
    public static final int MAX_SCREEN_COL = 16;
    public static final int MAX_SCREEN_ROW = 12;
    public static final int SCREEN_WIDTH = TILE_SIZE * MAX_SCREEN_COL;
    public static final int SCREEN_HEIGHT = TILE_SIZE * MAX_SCREEN_ROW;
    private final KeyHandler keyHandler;
    private final RTSCamera camera;
    private final TileManager tileManager;
    private final UnitManager unitManager;
    private final MouseHandler mouseHandler;
    private final MatchSession matchSession;
    private final Consumer<MatchResult> onMatchResolved;
    private final Runnable onQuitMatch;
    private static final long NANOS_PER_SEC = 1_000_000_000L;
    private static final double TARGET_TPS = 128.0;
    public static final double UPDATE_RATE = NANOS_PER_SEC / TARGET_TPS;
    private static final int BRIDGE_BUILD_TICKS = (int)(TARGET_TPS * 3.5);
    private static final int FORT_BUILD_TICKS = (int)(TARGET_TPS * 6.0);
    private static final double CONSTRUCTION_SUPPORT_RADIUS = TILE_SIZE * 4.75;
    private final Thread gameThread;
    private boolean gameThreadRunning;
    private static final String SANS_SERIF = "SansSerif";
    private static final Font HUD_TITLE_FONT = new Font(SANS_SERIF, Font.BOLD, 12);
    private static final Font HUD_VALUE_FONT = new Font(SANS_SERIF, Font.PLAIN, 12);
    private static final Font HUD_SELECTION_NAME_FONT = new Font(SANS_SERIF, Font.BOLD, 13);
    private static final Font HUD_SELECTION_STATUS_FONT = new Font(SANS_SERIF, Font.BOLD, 12);
    private static final Font CONSTRUCTION_PERCENT_FONT = new Font(SANS_SERIF, Font.BOLD, 10);
    private static final Color HUD_PANEL_BG = new Color(20, 20, 20, 185);
    private static final Color HUD_PANEL_OUTLINE = new Color(230, 230, 230, 210);
    private static final Color HUD_TEXT_PRIMARY = Color.WHITE;
    private static final Color HUD_TEXT_SECONDARY = new Color(220, 220, 220);
    private static final Color HUD_TEXT_TERTIARY = new Color(228, 228, 228);
    private static final Color SELECTION_BOX_FILL = new Color(0, 255, 0, 100);
    private static final Color FORMATION_LINE_COLOR = new Color(0, 200, 255, 180);
    private static final Color SUPPLY_OK_COLOR = new Color(72, 190, 96);
    private static final Color SUPPLY_BAD_COLOR = new Color(205, 74, 74);
    private static final Color CONSTRUCTION_TILE_FILL = new Color(208, 168, 92, 95);
    private static final Color CONSTRUCTION_TILE_OUTLINE = new Color(230, 206, 154, 220);
    private static final Color CONSTRUCTION_BAR_BG = new Color(40, 32, 24, 220);
    private static final Color CONSTRUCTION_BRIDGE_BAR = new Color(176, 121, 58);
    private static final Color CONSTRUCTION_FORT_BAR = new Color(164, 138, 102);
    private static final String[] KEYBIND_LINES = {"Keybinds", "WASD / Shift  Camera", "Wheel  Zoom",
            "LMB  Select / Build", "RMB  Pathfind Move", "MMB  Manual Move", "Ctrl+RMB  Formation", "R  Weapon Mode",
            "G  Toggle Forage", "B / F  Build Bridge or Fort", "Esc  Cancel Build / Quit Match", "F11  Fullscreen"};
    private Rectangle windowedBounds;
    private boolean windowedFullscreen;
    private volatile int displayedFps;
    private volatile int displayedTps;
    private final AtomicInteger renderedFrames = new AtomicInteger();
    private volatile boolean repaintPending;
    private final List<ConstructionSite> constructionSites = new ArrayList<>();
    private final Object constructionSitesLock = new Object();
    private StructureType pendingStructureType;
    private volatile boolean quitPromptOpen;
    private volatile boolean matchResolutionHandled;

    public GamePanel() { this(null, null, null, null, null); }

    public GamePanel(TileMap initialMap, CampaignManager.ScenarioDefinition scenarioDefinition) {
        this(initialMap, scenarioDefinition, null, null, null);
    }

    public GamePanel(TileMap initialMap, CampaignManager.ScenarioDefinition scenarioDefinition,
            MatchSession matchSession, Consumer<MatchResult> onMatchResolved, Runnable onQuitMatch) {
        this.keyHandler = new KeyHandler();
        this.camera = new RTSCamera(keyHandler);
        this.tileManager = initialMap != null ? new TileManager(this, initialMap) : new TileManager(this);
        this.unitManager = new UnitManager(this, scenarioDefinition);
        this.mouseHandler = new MouseHandler(this, camera);
        this.matchSession = matchSession;
        this.onMatchResolved = onMatchResolved;
        this.onQuitMatch = onQuitMatch;
        if (matchSession != null) {
            this.unitManager.setPlayerCulture(matchSession.playerCulture());
        }
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.black);
        this.setDoubleBuffered(true);
        this.addKeyListener(keyHandler);
        this.addMouseListener(mouseHandler);
        this.addMouseMotionListener(mouseHandler);
        this.addMouseWheelListener(mouseHandler);
        this.setFocusable(true);
        this.gameThread = new Thread(this, "GAME THREAD");
    }

    public void startGameThread() {
        if (gameThreadRunning) {
            return;
        }
        syncFullscreenStateFromWindow();
        gameThreadRunning = true;
        gameThread.start();
    }

    @Override
    public void run() {
        double accumulator = 0;
        long currentTime;
        long lastUpdate = System.nanoTime();
        // track FPS/TPS for output
        long timer = System.nanoTime();
        int ticks = 0;
        while (this.gameThreadRunning) {
            currentTime = System.nanoTime();
            long lastRenderTimeNano = currentTime - lastUpdate;
            lastUpdate = currentTime;
            accumulator += lastRenderTimeNano;
            // update game logic with a fixed timestep
            while (accumulator >= UPDATE_RATE) {
                // update game logic, physics, and movement
                update();
                ticks++;
                accumulator -= UPDATE_RATE;
            }
            requestRender();
            if (System.nanoTime() - timer > 1_000_000_000L) {
                timer += 1_000_000_000L;
                displayedFps = renderedFrames.getAndSet(0);
                displayedTps = ticks;
                ticks = 0;
            }
            if (repaintPending && accumulator < UPDATE_RATE * 0.5) {
                LockSupport.parkNanos(500_000L);
            }
        }
    }

    private void requestRender() {
        if (repaintPending) {
            return;
        }
        repaintPending = true;
        repaint();
    }

    public void update() {
        if (quitPromptOpen || matchResolutionHandled) {
            return;
        }
        handleRuntimeActions();
        updateConstructionSites();
        unitManager.update();
        evaluateMatchOutcome();
        camera.update();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        repaintPending = false;
        renderedFrames.incrementAndGet();
        Graphics2D g2d = (Graphics2D)g;
        AffineTransform originalTransform = g2d.getTransform();
        ViewportMetrics viewport = getViewportMetrics();
        Rectangle2D visibleWorldBounds = getVisibleWorldBounds();
        g2d.translate(viewport.offsetX(), viewport.offsetY());
        g2d.scale(viewport.scale(), viewport.scale());
        g2d.scale(camera.getZoomLevel(), camera.getZoomLevel());
        g2d.translate(-camera.getX(), -camera.getY());
        tileManager.draw(g2d, visibleWorldBounds);
        drawConstructionSites(g2d, visibleWorldBounds);
        unitManager.draw(g2d, visibleWorldBounds);
        if (mouseHandler.isDrawing()) {
            g2d.setColor(SELECTION_BOX_FILL);
            int mouseStartX = mouseHandler.getStartX();
            int mouseEndX = mouseHandler.getEndX();
            int mouseStartY = mouseHandler.getStartY();
            int mouseEndY = mouseHandler.getEndY();
            int x = Math.min(mouseStartX, mouseEndX);
            int y = Math.min(mouseStartY, mouseEndY);
            int width = Math.abs(mouseStartX - mouseEndX);
            int height = Math.abs(mouseStartY - mouseEndY);
            g2d.fillRect(x, y, width, height);
            g2d.setColor(Color.GREEN);
            g2d.drawRect(x, y, width, height);
        }
        if (mouseHandler.isFormationDrawing()) {
            List<Point> formationPoints = mouseHandler.getFormationPoints();
            g2d.setColor(FORMATION_LINE_COLOR);
            for (int index = 1; index < formationPoints.size(); index++) {
                Point startPoint = formationPoints.get(index - 1);
                Point endPoint = formationPoints.get(index);
                g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
            }
            if (!formationPoints.isEmpty()) {
                Point firstPoint = formationPoints.get(0);
                Point lastPoint = formationPoints.get(formationPoints.size() - 1);
                g2d.fillOval(firstPoint.x - 3, firstPoint.y - 3, 6, 6);
                g2d.fillOval(lastPoint.x - 3, lastPoint.y - 3, 6, 6);
            }
        }
        g2d.setTransform(originalTransform);
        int nextTopHudY = drawHoverTileHud(g2d, 14);
        drawSelectionHud(g2d, nextTopHudY);
        drawPerformanceHud(g2d);
        drawBuildHud(g2d);
        drawKeybindsHud(g2d);
        g2d.dispose();
    }

    private int drawHoverTileHud(Graphics2D g2d, int panelY) {
        HoveredTileInfo hoveredTile = resolveHoveredTileInfo();
        if (hoveredTile == null) {
            return panelY;
        }
        String[] lines = {"Hover Tile", "Terrain: " + hoveredTile.terrainType().getDisplayName(),
                "Structure: " + hoveredTile.structureType().getDisplayName(),
                "Grid: " + hoveredTile.col() + ", " + hoveredTile.row()};
        g2d.setFont(HUD_TITLE_FONT);
        int panelWidth = g2d.getFontMetrics(HUD_TITLE_FONT).stringWidth(lines[0]);
        g2d.setFont(HUD_VALUE_FONT);
        for (int index = 1; index < lines.length; index++) {
            panelWidth = Math.max(panelWidth, g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(lines[index]));
        }
        panelWidth += 24;
        int panelHeight = 18 + (lines.length - 1) * 15 + 14;
        int panelX = 14;
        drawHudPanelFrame(g2d, panelX, panelY, panelWidth, panelHeight);
        g2d.setFont(HUD_TITLE_FONT);
        g2d.setColor(HUD_TEXT_PRIMARY);
        g2d.drawString(lines[0], panelX + 12, panelY + 17);
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_SECONDARY);
        for (int index = 1; index < lines.length; index++) {
            g2d.drawString(lines[index], panelX + 12, panelY + 17 + index * 15);
        }
        return panelY + panelHeight + 10;
    }

    private void drawSelectionHud(Graphics2D g2d, int panelY) {
        List<Unit> selectedUnits = unitManager.getSelectedUnits();
        if (selectedUnits.isEmpty()) {
            return;
        }
        Unit primaryUnit = selectedUnits.getFirst();
        boolean showModeLabel = selectedUnits.size() == 1 && primaryUnit.supportsWeaponModeToggle();
        boolean showStatLines = selectedUnits.size() == 1;
        String[] statLines = showStatLines ? buildStatLines(primaryUnit) : new String[0];
        int panelX = 14;
        HudPanelLayout layout = buildSelectionHudLayout(selectedUnits.size(), showModeLabel, statLines.length);
        drawHudPanelFrame(g2d, panelX, panelY, layout.width(), layout.height());
        drawSelectionHeader(g2d, primaryUnit, panelX, panelY);
        drawSelectionSecondaryLine(g2d, selectedUnits.size(), primaryUnit, showModeLabel, panelX, panelY);
        drawSelectionStats(g2d, statLines, showModeLabel, panelX, panelY);
    }

    private HudPanelLayout buildSelectionHudLayout(int selectedCount, boolean showModeLabel, int statLineCount) {
        int panelWidth = selectedCount > 1 ? 250 : 270;
        int panelHeight = selectedCount > 1 ? 74 : 58;
        if (showModeLabel) {
            panelHeight += 18;
            panelWidth = Math.max(panelWidth, 240);
        }
        if (statLineCount > 0) {
            panelHeight += statLineCount * 16 + 10;
        }
        return new HudPanelLayout(panelWidth, panelHeight);
    }

    private void drawHudPanelFrame(Graphics2D g2d, int panelX, int panelY, int panelWidth, int panelHeight) {
        g2d.setColor(HUD_PANEL_BG);
        g2d.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
        g2d.setColor(HUD_PANEL_OUTLINE);
        g2d.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
    }

    private void drawSelectionHeader(Graphics2D g2d, Unit primaryUnit, int panelX, int panelY) {
        String unitLabel = formatUnitName(primaryUnit);
        String supplyLabel = primaryUnit.isSupplied() ? "Supplied" : "Out of Supply";
        Color supplyColor = primaryUnit.isSupplied() ? SUPPLY_OK_COLOR : SUPPLY_BAD_COLOR;
        g2d.setFont(HUD_SELECTION_NAME_FONT);
        g2d.setColor(HUD_TEXT_PRIMARY);
        g2d.drawString(unitLabel, panelX + 12, panelY + 22);
        g2d.setFont(HUD_SELECTION_STATUS_FONT);
        g2d.setColor(supplyColor);
        g2d.drawString(supplyLabel, panelX + 12, panelY + 42);
    }

    private void drawSelectionSecondaryLine(Graphics2D g2d, int selectedCount, Unit primaryUnit, boolean showModeLabel,
            int panelX, int panelY) {
        String secondaryLabel = null;
        if (showModeLabel) {
            secondaryLabel = "Mode: " + primaryUnit.getWeaponModeDisplayName() + " (R)";
        } else if (selectedCount > 1) {
            int selectedForagingCount = unitManager.getSelectedForagingCount();
            secondaryLabel = selectedForagingCount > 0
                    ? "Selected: " + selectedCount + " units   Foraging: " + selectedForagingCount
                    : "Selected: " + selectedCount + " units";
        }
        if (secondaryLabel == null) {
            return;
        }
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_SECONDARY);
        g2d.drawString(secondaryLabel, panelX + 12, panelY + 60);
    }

    private void drawSelectionStats(Graphics2D g2d, String[] statLines, boolean showModeLabel, int panelX, int panelY) {
        if (statLines.length == 0) {
            return;
        }
        int statStartY = panelY + (showModeLabel ? 78 : 60);
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_TERTIARY);
        for (int index = 0; index < statLines.length; index++) {
            g2d.drawString(statLines[index], panelX + 12, statStartY + index * 16);
        }
    }

    private void drawPerformanceHud(Graphics2D g2d) {
        String fpsLabel = "FPS: " + displayedFps;
        String tpsLabel = "TPS: " + displayedTps;
        int foodReserve = unitManager.getPlayerFoodReserve();
        String foodLabel = "Food: " + formatWhole(foodReserve);
        g2d.setFont(HUD_TITLE_FONT);
        int textWidth = Math.max(g2d.getFontMetrics(HUD_TITLE_FONT).stringWidth(fpsLabel),
                Math.max(g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(tpsLabel),
                        g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(foodLabel)));
        int panelWidth = textWidth + 24;
        int panelHeight = 57;
        int panelX = getWidth() - panelWidth - 14;
        int panelY = 14;
        drawHudPanelFrame(g2d, panelX, panelY, panelWidth, panelHeight);
        g2d.setFont(HUD_TITLE_FONT);
        g2d.setColor(HUD_TEXT_PRIMARY);
        g2d.drawString(fpsLabel, panelX + 12, panelY + 17);
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_SECONDARY);
        g2d.drawString(tpsLabel, panelX + 12, panelY + 32);
        g2d.setColor(foodReserve > 0 ? HUD_TEXT_SECONDARY : SUPPLY_BAD_COLOR);
        g2d.drawString(foodLabel, panelX + 12, panelY + 47);
    }

    private void drawBuildHud(Graphics2D g2d) {
        if (pendingStructureType == null || pendingStructureType == StructureType.NONE) {
            return;
        }
        String lineOne = "Build: " + pendingStructureType.getDisplayName();
        String lineTwo = "Left click start build, Esc cancel";
        String lineThree = constructionSites.isEmpty() ? "No active construction sites"
                : "Active sites: " + constructionSites.size();
        g2d.setFont(HUD_TITLE_FONT);
        int panelWidth = Math.max(g2d.getFontMetrics(HUD_TITLE_FONT).stringWidth(lineOne),
                Math.max(g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(lineTwo),
                        g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(lineThree)))
                + 24;
        int panelHeight = 58;
        int panelX = 14;
        int panelY = getHeight() - panelHeight - 14;
        drawHudPanelFrame(g2d, panelX, panelY, panelWidth, panelHeight);
        g2d.setFont(HUD_TITLE_FONT);
        g2d.setColor(HUD_TEXT_PRIMARY);
        g2d.drawString(lineOne, panelX + 12, panelY + 17);
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_SECONDARY);
        g2d.drawString(lineTwo, panelX + 12, panelY + 32);
        g2d.drawString(lineThree, panelX + 12, panelY + 47);
    }

    private void drawKeybindsHud(Graphics2D g2d) {
        g2d.setFont(HUD_TITLE_FONT);
        int panelWidth = g2d.getFontMetrics(HUD_TITLE_FONT).stringWidth(KEYBIND_LINES[0]);
        g2d.setFont(HUD_VALUE_FONT);
        for (int index = 1; index < KEYBIND_LINES.length; index++) {
            panelWidth = Math.max(panelWidth, g2d.getFontMetrics(HUD_VALUE_FONT).stringWidth(KEYBIND_LINES[index]));
        }
        panelWidth += 24;
        int panelHeight = 18 + (KEYBIND_LINES.length - 1) * 15 + 14;
        int panelX = getWidth() - panelWidth - 14;
        int panelY = getHeight() - panelHeight - 14;
        drawHudPanelFrame(g2d, panelX, panelY, panelWidth, panelHeight);
        g2d.setFont(HUD_TITLE_FONT);
        g2d.setColor(HUD_TEXT_PRIMARY);
        g2d.drawString(KEYBIND_LINES[0], panelX + 12, panelY + 17);
        g2d.setFont(HUD_VALUE_FONT);
        g2d.setColor(HUD_TEXT_SECONDARY);
        for (int index = 1; index < KEYBIND_LINES.length; index++) {
            g2d.drawString(KEYBIND_LINES[index], panelX + 12, panelY + 17 + index * 15);
        }
    }

    private void drawConstructionSites(Graphics2D g2d, Rectangle2D visibleWorldBounds) {
        ConstructionSite[] sites = getConstructionSitesSnapshot();
        if (sites.length == 0) {
            return;
        }
        Font percentFont = CONSTRUCTION_PERCENT_FONT;
        for (ConstructionSite site : sites) {
            double tileX = (double)site.col() * TILE_SIZE;
            double tileY = (double)site.row() * TILE_SIZE;
            if (visibleWorldBounds != null && !visibleWorldBounds.intersects(tileX, tileY, TILE_SIZE, TILE_SIZE)) {
                continue;
            }
            double progressRatio = site.progressRatio();
            g2d.setColor(CONSTRUCTION_TILE_FILL);
            g2d.fillRect((int)tileX, (int)tileY, TILE_SIZE, TILE_SIZE);
            g2d.setColor(CONSTRUCTION_TILE_OUTLINE);
            g2d.drawRect((int)tileX, (int)tileY, TILE_SIZE - 1, TILE_SIZE - 1);
            int barX = (int)tileX + 5;
            int barY = (int)tileY + TILE_SIZE - 10;
            int barWidth = TILE_SIZE - 10;
            g2d.setColor(CONSTRUCTION_BAR_BG);
            g2d.fillRect(barX, barY, barWidth, 4);
            g2d.setColor(site.type().isBridge() ? CONSTRUCTION_BRIDGE_BAR : CONSTRUCTION_FORT_BAR);
            g2d.fillRect(barX, barY, (int)Math.round(barWidth * progressRatio), 4);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(barX, barY, barWidth, 4);
            g2d.setFont(percentFont);
            g2d.setColor(Color.WHITE);
            g2d.drawString((int)Math.round(progressRatio * 100.0) + "%", (int)tileX + 6, (int)tileY + 14);
        }
    }

    private String[] buildStatLines(Unit unit) {
        return new String[] {"Health: " + formatDecimal(unit.getHealth()) + " / " + formatDecimal(unit.getMaxHealth()),
                "Attack: " + formatDecimal(unit.getAttackPower()) + "   Defense: " + formatDecimal(unit.getDefense()),
                "Range: " + formatDecimal(unit.getAttackRange()) + "   Rate: " + formatDecimal(unit.getRateOfFire())
                        + "/s",
                "Weapon: " + unit.getWeaponEffectiveness() + "   Armor: " + unit.getArmorEffectiveness()
                        + "   Mobility: " + unit.getMobility(),
                "Morale: " + unit.getMorale() + "   Cohesion: " + unit.getCohesion() + "   Stamina: "
                        + unit.getStamina(),
                "Food: " + formatWhole(unit.getCarriedFood()) + " / " + formatWhole(unit.getFoodCarryCapacity())
                        + "   Foraging: " + (unit.isForaging() ? "Yes" : "No"),
                "Size: " + unit.getUnitSize() + "   Experience: " + unit.getExperienceLevelNum() + " ("
                        + formatExperienceLevel(unit) + ")"};
    }

    private String formatDecimal(double value) { return String.format("%.1f", value); }

    private String formatWhole(int value) { return String.format("%,d", value); }

    private String formatExperienceLevel(Unit unit) {
        String rawValue = unit.getExperienceLevel().name().toLowerCase();
        return Character.toUpperCase(rawValue.charAt(0)) + rawValue.substring(1);
    }

    private void handleRuntimeActions() {
        if (keyHandler.consumeFullScreenToggleRequested()) {
            toggleWindowedFullscreen();
        }
        if (keyHandler.consumeWeaponModeToggleRequested()) {
            unitManager.cycleSelectedHeavyInfantryModes();
        }
        if (keyHandler.consumeBridgeBuildRequested()) {
            togglePendingStructureType(StructureType.BRIDGE);
        }
        if (keyHandler.consumeFortBuildRequested()) {
            togglePendingStructureType(StructureType.FORT);
        }
        if (keyHandler.consumeForageToggleRequested()) {
            unitManager.toggleSelectedForaging();
        }
        if (keyHandler.consumeBuildCancelRequested()) {
            if (pendingStructureType != null) {
                pendingStructureType = null;
            } else {
                requestQuitMatch();
            }
        }
    }

    private void requestQuitMatch() {
        if (quitPromptOpen || onQuitMatch == null) {
            return;
        }
        quitPromptOpen = true;
        SwingUtilities.invokeLater(() -> {
            Object[] options = {"Quit Match", "Continue"};
            int choice = JOptionPane.showOptionDialog(this, "Quit the current match and return to the main menu?",
                    "Quit Match", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            quitPromptOpen = false;
            if (choice == JOptionPane.YES_OPTION) {
                stopGameThread();
                onQuitMatch.run();
                return;
            }
            requestFocusInWindow();
        });
    }

    private void evaluateMatchOutcome() {
        if (matchSession == null || matchResolutionHandled) {
            return;
        }
        MatchResult result = resolveMatchResult();
        if (result == null) {
            return;
        }
        matchResolutionHandled = true;
        stopGameThread();
        if (onMatchResolved != null) {
            SwingUtilities.invokeLater(() -> onMatchResolved.accept(result));
        }
    }

    private MatchResult resolveMatchResult() {
        AliveState aliveState = getAliveState();
        if (aliveState.playerAlive() && aliveState.enemyAlive()) {
            return null;
        }
        if (!aliveState.playerAlive() && !aliveState.enemyAlive()) {
            return null;
        }
        boolean victory = aliveState.playerAlive() && !aliveState.enemyAlive();
        int rewardCoins = victory ? matchSession.calculateWinCoins() : 0;
        return new MatchResult(matchSession.battleName(), matchSession.matchMode(), victory, rewardCoins);
    }

    private AliveState getAliveState() {
        boolean playerAlive = false;
        boolean enemyAlive = false;
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            if (unit.getHealth() <= 0 || unit.hasSurrendered()) {
                continue;
            }
            if (unit.getSoldierCulture() == unitManager.getPlayerCulture()) {
                playerAlive = true;
            } else {
                enemyAlive = true;
            }
            if (playerAlive && enemyAlive) {
                break;
            }
        }
        return new AliveState(playerAlive, enemyAlive);
    }

    public void stopGameThread() {
        gameThreadRunning = false;
        unitManager.shutdown();
    }

    private void togglePendingStructureType(StructureType structureType) {
        pendingStructureType = pendingStructureType == structureType ? null : structureType;
    }

    public void setPendingStructureType(StructureType structureType) {
        pendingStructureType = structureType == StructureType.NONE ? null : structureType;
    }

    private void updateConstructionSites() {
        TileMap activeMap = tileManager.getTileMap();
        if (activeMap == null) {
            synchronized (constructionSitesLock) {
                constructionSites.clear();
            }
            return;
        }
        synchronized (constructionSitesLock) {
            if (constructionSites.isEmpty()) {
                return;
            }
            Iterator<ConstructionSite> iterator = constructionSites.iterator();
            while (iterator.hasNext()) {
                ConstructionSite site = iterator.next();
                if (activeMap.getStructureType(site.col(), site.row()) == site.type()) {
                    iterator.remove();
                    continue;
                }
                int builderCount = countSupportingBuilders(site, false);
                if (builderCount <= 0) {
                    continue;
                }
                site.advance(builderCount);
                if (site.isComplete()) {
                    activeMap.setStructureType(site.col(), site.row(), site.type());
                    iterator.remove();
                }
            }
        }
    }

    private int countSupportingBuilders(ConstructionSite site, boolean selectedOnly) {
        int buildX = site.centerX();
        int buildY = site.centerY();
        double radiusSq = CONSTRUCTION_SUPPORT_RADIUS * CONSTRUCTION_SUPPORT_RADIUS;
        int builders = 0;
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            if (unit.getSoldierCulture() != site.builderCulture() || unit.hasSurrendered() || unit.getHealth() <= 0) {
                continue;
            }
            if (selectedOnly && !unit.isSelected()) {
                continue;
            }
            double dx = (double)unit.getX() - buildX;
            double dy = (double)unit.getY() - buildY;
            if (dx * dx + dy * dy <= radiusSq) {
                builders++;
            }
        }
        return builders;
    }

    private boolean hasConstructionSiteAt(int col, int row) {
        synchronized (constructionSitesLock) {
            for (ConstructionSite site : constructionSites) {
                if (site.col() == col && site.row() == row) {
                    return true;
                }
            }
            return false;
        }
    }

    private void syncFullscreenStateFromWindow() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (!(window instanceof JFrame frame)) {
            return;
        }
        windowedFullscreen = isBorderlessFullscreen(frame);
        if (!windowedFullscreen) {
            windowedBounds = frame.getBounds();
        }
    }

    private boolean isBorderlessFullscreen(JFrame frame) {
        return frame.isUndecorated() && frame.getGraphicsConfiguration() != null
                && frame.getGraphicsConfiguration().getBounds().equals(frame.getBounds());
    }

    private void toggleWindowedFullscreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (!(window instanceof JFrame frame)) {
            return;
        }
        if (!windowedFullscreen) {
            windowedBounds = frame.getBounds();
            frame.dispose();
            frame.setUndecorated(true);
            frame.setBounds(frame.getGraphicsConfiguration().getBounds());
            frame.setVisible(true);
            windowedFullscreen = true;
        } else {
            frame.dispose();
            frame.setUndecorated(false);
            if (windowedBounds != null) {
                frame.setBounds(windowedBounds);
            } else {
                frame.setSize(SCREEN_WIDTH + 16, SCREEN_HEIGHT + 39);
                frame.setLocationRelativeTo(null);
            }
            frame.setVisible(true);
            windowedFullscreen = false;
        }
        requestFocusInWindow();
    }

    public Point screenToWorld(int screenX, int screenY) {
        Point viewportPoint = screenToViewport(screenX, screenY);
        int worldX = (int)Math.round(viewportPoint.x / camera.getZoomLevel() + camera.getX());
        int worldY = (int)Math.round(viewportPoint.y / camera.getZoomLevel() + camera.getY());
        return new Point(worldX, worldY);
    }

    public Point screenToViewport(int screenX, int screenY) {
        ViewportMetrics viewport = getViewportMetrics();
        int viewportX = (int)Math.round((screenX - viewport.offsetX()) / viewport.scale());
        int viewportY = (int)Math.round((screenY - viewport.offsetY()) / viewport.scale());
        return new Point(viewportX, viewportY);
    }

    private HoveredTileInfo resolveHoveredTileInfo() {
        TileMap activeMap = tileManager.getTileMap();
        Point hoverScreenCoords = mouseHandler.getHoverScreenCoords();
        if (activeMap == null || hoverScreenCoords == null || !isWithinViewport(hoverScreenCoords)) {
            return null;
        }
        Point worldPoint = screenToWorld(hoverScreenCoords.x, hoverScreenCoords.y);
        int col = (int)Math.floor(worldPoint.x / (double)TILE_SIZE);
        int row = (int)Math.floor(worldPoint.y / (double)TILE_SIZE);
        if (col < 0 || row < 0 || col >= activeMap.getWidth() || row >= activeMap.getHeight()) {
            return null;
        }
        return new HoveredTileInfo(col, row, activeMap.getTerrainType(col, row), activeMap.getStructureType(col, row));
    }

    private boolean isWithinViewport(Point screenPoint) {
        ViewportMetrics viewport = getViewportMetrics();
        int viewportWidth = (int)Math.round(SCREEN_WIDTH * viewport.scale());
        int viewportHeight = (int)Math.round(SCREEN_HEIGHT * viewport.scale());
        return screenPoint.x >= viewport.offsetX() && screenPoint.y >= viewport.offsetY()
                && screenPoint.x < viewport.offsetX() + viewportWidth
                && screenPoint.y < viewport.offsetY() + viewportHeight;
    }

    private ViewportMetrics getViewportMetrics() {
        int panelWidth = Math.max(1, getWidth());
        int panelHeight = Math.max(1, getHeight());
        double scale = Math.min(panelWidth / (double)SCREEN_WIDTH, panelHeight / (double)SCREEN_HEIGHT);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            scale = 1.0;
        }
        int viewportWidth = (int)Math.round(SCREEN_WIDTH * scale);
        int viewportHeight = (int)Math.round(SCREEN_HEIGHT * scale);
        int offsetX = (panelWidth - viewportWidth) / 2;
        int offsetY = (panelHeight - viewportHeight) / 2;
        return new ViewportMetrics(scale, offsetX, offsetY);
    }

    private Rectangle2D getVisibleWorldBounds() {
        double zoom = Math.max(0.0001, camera.getZoomLevel());
        double visibleWidth = SCREEN_WIDTH / zoom;
        double visibleHeight = SCREEN_HEIGHT / zoom;
        double padding = TILE_SIZE * 2.0;
        return new Rectangle2D.Double(camera.getX() - padding, camera.getY() - padding, visibleWidth + padding * 2.0,
                visibleHeight + padding * 2.0);
    }

    private record ViewportMetrics(double scale, int offsetX, int offsetY) {}

    private record HudPanelLayout(int width, int height) {}

    private record HoveredTileInfo(int col, int row, TerrainType terrainType, StructureType structureType) {}

    private record AliveState(boolean playerAlive, boolean enemyAlive) {}

    private String formatUnitName(Unit unit) {
        String name = unit.getSoldierType().name().replace('_', ' ').toLowerCase();
        String[] words = name.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1));
        }
        return builder.toString();
    }

    public void updateSelectedUnits(Rectangle2D rect) {
        for (Unit unit : UnitManager.getUnitsSnapshot()) {
            unit.setSelected(unit.intersects(rect) && unit.getSoldierCulture() == unitManager.getPlayerCulture());
        }
    }

    public boolean hasPendingStructureBuild() {
        return pendingStructureType != null && pendingStructureType != StructureType.NONE;
    }

    public boolean tryBuildStructure(Point worldPoint) {
        if (!hasPendingStructureBuild() || worldPoint == null) {
            return false;
        }
        TileMap activeMap = tileManager.getTileMap();
        if (activeMap == null) {
            return false;
        }
        int col = Math.clamp(worldPoint.x / TILE_SIZE, 0, activeMap.getWidth() - 1);
        int row = Math.clamp(worldPoint.y / TILE_SIZE, 0, activeMap.getHeight() - 1);
        if (!activeMap.canPlaceStructure(col, row, pendingStructureType) || hasConstructionSiteAt(col, row)
                || activeMap.getStructureType(col, row) == pendingStructureType) {
            return false;
        }
        ConstructionSite site = new ConstructionSite(col, row, pendingStructureType, unitManager.getPlayerCulture(),
                pendingStructureType.isBridge() ? BRIDGE_BUILD_TICKS : FORT_BUILD_TICKS);
        if (countSupportingBuilders(site, true) <= 0) {
            return false;
        }
        synchronized (constructionSitesLock) {
            constructionSites.add(site);
        }
        repaint();
        return true;
    }

    public int getConstructionSiteCount() {
        synchronized (constructionSitesLock) {
            return constructionSites.size();
        }
    }

    public double getConstructionProgressAt(int col, int row) {
        synchronized (constructionSitesLock) {
            for (ConstructionSite site : constructionSites) {
                if (site.col() == col && site.row() == row) {
                    return site.progressRatio();
                }
            }
            return 0.0;
        }
    }

    private ConstructionSite[] getConstructionSitesSnapshot() {
        synchronized (constructionSitesLock) {
            return constructionSites.toArray(ConstructionSite[]::new);
        }
    }

    private final class ConstructionSite {
        private final int col;
        private final int row;
        private final int centerX;
        private final int centerY;
        private final StructureType type;
        private final Unit.SoldierCulture builderCulture;
        private final int requiredTicks;
        private double progressTicks;

        private ConstructionSite(int col, int row, StructureType type, Unit.SoldierCulture builderCulture,
                int requiredTicks) {
            this.col = col;
            this.row = row;
            this.centerX = col * TILE_SIZE + TILE_SIZE / 2;
            this.centerY = row * TILE_SIZE + TILE_SIZE / 2;
            this.type = type;
            this.builderCulture = builderCulture;
            this.requiredTicks = Math.max(1, requiredTicks);
        }

        private int col() { return col; }

        private int row() { return row; }

        private StructureType type() { return type; }

        private Unit.SoldierCulture builderCulture() { return builderCulture; }

        private int centerX() { return centerX; }

        private int centerY() { return centerY; }

        private void advance(int builderCount) { progressTicks += Math.min(2.35, 0.55 + builderCount * 0.55); }

        private boolean isComplete() { return progressTicks >= requiredTicks; }

        private double progressRatio() { return Math.clamp(progressTicks / requiredTicks, 0.0, 1.0); }
    }

    public int getMaxScreenCol() { return MAX_SCREEN_COL; }

    public int getMaxScreenRow() { return MAX_SCREEN_ROW; }

    public int getScreenWidth() { return SCREEN_WIDTH; }

    public int getScreenHeight() { return SCREEN_HEIGHT; }

    public RTSCamera getCamera() { return camera; }

    public TileManager getTileManager() { return tileManager; }

    public UnitManager getUnitManager() { return unitManager; }

    public boolean isRunning() { return gameThreadRunning; }
}
