package main;

import campaign.CampaignManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import units.Unit;
import world.GameWorld.StructureType;
import world.GameWorld.TerrainType;
import world.StructureRenderHelper;
import world.TileMap;

public class MapEditorPanel extends JPanel {
    private static final int EDITOR_TILE_SIZE = 28;
    private static final int MAX_EDITOR_MAP_DIMENSION = 1024;
    private static final String SANS_SERIF = "SansSerif";
    private static final String SAVE_ERROR = "Save Error";
    private static final String LOAD_ERROR = "Load Error";
    private final Runnable onBack;
    private final BiConsumer<TileMap, CampaignManager.ScenarioDefinition> onPlayScenario;
    private final CampaignManager campaignManager = new CampaignManager();
    private TileMap tileMap;
    private TerrainType selectedTerrain;
    private StructureType selectedStructure;
    private final MapCanvas mapCanvas;
    private final JLabel statusLabel;
    private final JTextField nameField;
    private final JComboBox<String> battlefieldTypeBox;
    private final JTextArea historicalNotesArea;
    private final JComboBox<TerrainType> terrainBox;
    private final JComboBox<StructureType> structureBox;
    private final JComboBox<Unit.SoldierCulture> cultureBox;
    private final JComboBox<Unit.SoldierType> typeBox;
    private final JComboBox<Integer> sizeBox;
    private final JSpinner experienceSpinner;
    private final JRadioButton terrainModeButton;
    private final JRadioButton structureModeButton;
    private final JRadioButton unitModeButton;
    private final List<CampaignManager.ForcePlacement> forcePlacements = new ArrayList<>();
    private Map<Unit.SoldierCulture, Integer> startingFood = new LinkedHashMap<>();
    private Map<Unit.SoldierCulture, CampaignManager.AIDirective> aiDirectives = new LinkedHashMap<>();
    private File currentMapFile;
    private File currentScenarioFile;

    public MapEditorPanel(Runnable onBack, BiConsumer<TileMap, CampaignManager.ScenarioDefinition> onPlayScenario) {
        this.onBack = onBack;
        this.onPlayScenario = onPlayScenario;
        this.tileMap = TileMap.createBlank(32, 24);
        this.selectedTerrain = TerrainType.FLAT_GRASS;
        this.selectedStructure = StructureType.FORT;
        this.mapCanvas = new MapCanvas();
        this.statusLabel = new JLabel("", SwingConstants.LEFT);
        this.nameField = new JTextField("Custom Scenario");
        this.battlefieldTypeBox = new JComboBox<>(new String[] {"custom", "historical", "sandbox"});
        this.historicalNotesArea = new JTextArea(5, 20);
        this.terrainBox = new JComboBox<>(TerrainType.values());
        this.structureBox = new JComboBox<>(StructureType.values());
        this.cultureBox = new JComboBox<>(Unit.SoldierCulture.values());
        this.typeBox = new JComboBox<>(Unit.SoldierType.values());
        this.sizeBox = new JComboBox<>(new Integer[] {100, 500, 1000, 2000, 5000});
        this.experienceSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        this.terrainModeButton = new JRadioButton("Paint Terrain", true);
        this.structureModeButton = new JRadioButton("Place Structures");
        this.structureModeButton.setForeground(Color.WHITE);
        this.terrainModeButton.setForeground(Color.WHITE);
        this.unitModeButton = new JRadioButton("Place Units");
        this.unitModeButton.setForeground(Color.WHITE);
        this.terrainBox.setSelectedItem(selectedTerrain);
        this.structureBox.setSelectedItem(selectedStructure);
        this.terrainBox.addActionListener(event -> {
            TerrainType terrainType = (TerrainType)terrainBox.getSelectedItem();
            selectedTerrain = terrainType;
            updateStatus();
        });
        this.structureBox.addActionListener(event -> {
            StructureType structureType = (StructureType)structureBox.getSelectedItem();
            selectedStructure = structureType;
            updateStatus();
        });
        setLayout(new BorderLayout());
        setBackground(new Color(43, 35, 28));
        setPreferredSize(new Dimension(1240, 820));
        add(createToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(mapCanvas), BorderLayout.CENTER);
        add(createSidebar(), BorderLayout.EAST);
        add(statusLabel, BorderLayout.SOUTH);
        updateCanvasState();
    }

    private JPanel createToolbar() {
        JPanel root = new JPanel(new GridLayout(1, 7, 8, 0));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.setBackground(new Color(59, 46, 35));
        root.add(createToolbarButton("New Map", this::createNewMap));
        root.add(createToolbarButton("Load Map", this::loadMap));
        root.add(createToolbarButton("Load Scenario", this::loadScenario));
        root.add(createToolbarButton("Save Map", this::saveMap));
        root.add(createToolbarButton("Save Scenario", this::saveScenario));
        root.add(createToolbarButton("Play Scenario", this::playScenario));
        root.add(createToolbarButton("Back", onBack));
        return root;
    }

    private JButton createToolbarButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(54, 44, 34));
        sidebar.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(290, 0));
        sidebar.add(createSectionLabel("Metadata"));
        sidebar.add(createLabeledField("Name", nameField));
        sidebar.add(createLabeledField("Battlefield Type", battlefieldTypeBox));
        historicalNotesArea.setLineWrap(true);
        historicalNotesArea.setWrapStyleWord(true);
        historicalNotesArea.setFont(new Font(SANS_SERIF, Font.PLAIN, 12));
        JScrollPane notesScroll = new JScrollPane(historicalNotesArea);
        notesScroll.setPreferredSize(new Dimension(240, 120));
        sidebar.add(createLabeledField("Historical Notes", notesScroll));
        sidebar.add(createSectionLabel("Placement Mode"));
        ButtonGroup modeGroup = new ButtonGroup();
        terrainModeButton.setOpaque(false);
        structureModeButton.setOpaque(false);
        unitModeButton.setOpaque(false);
        modeGroup.add(terrainModeButton);
        modeGroup.add(structureModeButton);
        modeGroup.add(unitModeButton);
        sidebar.add(terrainModeButton);
        sidebar.add(structureModeButton);
        sidebar.add(unitModeButton);
        sidebar.add(createSectionLabel("Terrain Painting"));
        sidebar.add(createLabeledField("Terrain", terrainBox));
        sidebar.add(createSectionLabel("Structures"));
        sidebar.add(createLabeledField("Structure", structureBox));
        sidebar.add(createSectionLabel("Unit Placement"));
        sidebar.add(createLabeledField("Culture", cultureBox));
        sidebar.add(createLabeledField("Type", typeBox));
        sidebar.add(createLabeledField("Unit Size", sizeBox));
        sidebar.add(createLabeledField("Experience", experienceSpinner));
        JLabel terrainHelpLabel = new JLabel("Terrain mode paints the selected terrain onto the map.");
        terrainHelpLabel.setForeground(new Color(233, 220, 194));
        terrainHelpLabel.setFont(new Font(SANS_SERIF, Font.PLAIN, 12));
        sidebar.add(terrainHelpLabel);
        JLabel structureHelpLabel = new JLabel("Structure mode places bridges, walls, or forts. Right click clears.");
        structureHelpLabel.setForeground(new Color(233, 220, 194));
        structureHelpLabel.setFont(new Font(SANS_SERIF, Font.PLAIN, 12));
        sidebar.add(structureHelpLabel);
        JLabel helpLabel = new JLabel("Left click places. Right click removes nearest.");
        helpLabel.setForeground(new Color(233, 220, 194));
        helpLabel.setFont(new Font(SANS_SERIF, Font.PLAIN, 12));
        sidebar.add(helpLabel);
        return sidebar;
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(247, 234, 205));
        label.setFont(new Font("Serif", Font.BOLD, 18));
        label.setBorder(BorderFactory.createEmptyBorder(12, 0, 8, 0));
        return label;
    }

    private JPanel createLabeledField(String labelText, java.awt.Component component) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(233, 220, 194));
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return panel;
    }

    private void createNewMap() {
        JSpinner widthSpinner = new JSpinner(
                new SpinnerNumberModel(tileMap.getWidth(), 8, MAX_EDITOR_MAP_DIMENSION, 1));
        JSpinner heightSpinner = new JSpinner(
                new SpinnerNumberModel(tileMap.getHeight(), 8, MAX_EDITOR_MAP_DIMENSION, 1));
        JComboBox<TerrainType> defaultTerrainBox = new JComboBox<>(TerrainType.values());
        defaultTerrainBox.setSelectedItem(selectedTerrain);
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Width"));
        panel.add(widthSpinner);
        panel.add(new JLabel("Height"));
        panel.add(heightSpinner);
        panel.add(new JLabel("Default Terrain"));
        panel.add(defaultTerrainBox);
        int result = JOptionPane.showConfirmDialog(this, panel, "Create New Map", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        TerrainType defaultTerrain = (TerrainType)defaultTerrainBox.getSelectedItem();
        tileMap = TileMap.createBlank(resolveBattleName(), (Integer)widthSpinner.getValue(),
                (Integer)heightSpinner.getValue(), defaultTerrain.ordinal());
        selectedTerrain = defaultTerrain;
        terrainBox.setSelectedItem(selectedTerrain);
        forcePlacements.clear();
        aiDirectives = new LinkedHashMap<>();
        currentMapFile = null;
        currentScenarioFile = null;
        updateCanvasState();
    }

    private void saveMap() {
        File targetFile = chooseMapSaveFile();
        if (targetFile == null) {
            return;
        }
        tileMap.setBattleName(resolveBattleName());
        if (!tileMap.saveToFile(targetFile.getAbsolutePath())) {
            JOptionPane.showMessageDialog(this, "Failed to save map.", SAVE_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        currentMapFile = targetFile;
        statusLabel.setText("Saved map to " + targetFile.getAbsolutePath());
    }

    private File chooseMapSaveFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Map");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Map (*.csv)", "csv"));
        if (currentMapFile != null) {
            chooser.setSelectedFile(currentMapFile);
        }
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File targetFile = chooser.getSelectedFile();
        if (!targetFile.getName().toLowerCase().endsWith(".csv")) {
            targetFile = new File(targetFile.getParentFile(), targetFile.getName() + ".csv");
        }
        return targetFile;
    }

    private void loadMap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Map");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Map (*.csv)", "csv"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        TileMap loadedMap = TileMap.fromFile(chooser.getSelectedFile());
        if (loadedMap == null) {
            JOptionPane.showMessageDialog(this, "Failed to load map.", LOAD_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        tileMap = loadedMap;
        currentMapFile = chooser.getSelectedFile();
        currentScenarioFile = null;
        forcePlacements.clear();
        startingFood = new LinkedHashMap<>();
        aiDirectives = new LinkedHashMap<>();
        if (nameField.getText().isBlank()) {
            nameField.setText(tileMap.getBattleName());
        }
        updateCanvasState();
        statusLabel.setText("Loaded map from " + chooser.getSelectedFile().getAbsolutePath());
    }

    private void loadScenario() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Scenario");
        chooser.setFileFilter(new FileNameExtensionFilter("Scenario CSV (*.csv)", "csv"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File scenarioFile = chooser.getSelectedFile();
        CampaignManager.ScenarioDefinition scenarioDefinition = campaignManager.loadScenarioDefinition(scenarioFile);
        if (scenarioDefinition == null) {
            JOptionPane.showMessageDialog(this, "Failed to load scenario.", LOAD_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        TileMap loadedMap = loadScenarioMap(scenarioDefinition.getMapPath(), scenarioFile);
        File mapFile = resolveMapFile(scenarioDefinition.getMapPath(), scenarioFile);
        if (loadedMap == null) {
            JOptionPane.showMessageDialog(this, "Failed to load the map referenced by this scenario.", LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        tileMap = loadedMap;
        currentMapFile = mapFile;
        currentScenarioFile = scenarioFile;
        nameField.setText(scenarioDefinition.getName());
        battlefieldTypeBox.setSelectedItem(scenarioDefinition.getBattlefieldType());
        historicalNotesArea.setText(scenarioDefinition.getHistoricalNotes());
        forcePlacements.clear();
        forcePlacements.addAll(scenarioDefinition.getForces());
        startingFood = scenarioDefinition.getStartingFood();
        aiDirectives = scenarioDefinition.getAiDirectives();
        updateCanvasState();
        statusLabel.setText("Loaded scenario from " + scenarioFile.getAbsolutePath());
    }

    private void saveScenario() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Scenario");
        chooser.setFileFilter(new FileNameExtensionFilter("Scenario CSV (*.csv)", "csv"));
        if (currentScenarioFile != null) {
            chooser.setSelectedFile(currentScenarioFile);
        }
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File scenarioFile = chooser.getSelectedFile();
        if (!scenarioFile.getName().toLowerCase().endsWith(".csv")) {
            scenarioFile = new File(scenarioFile.getParentFile(), scenarioFile.getName() + ".csv");
        }
        File mapFile = currentMapFile != null ? currentMapFile
                : new File(scenarioFile.getParentFile(), stripExtension(scenarioFile.getName()) + "_map.csv");
        tileMap.setBattleName(resolveBattleName());
        if (!tileMap.saveToFile(mapFile.getAbsolutePath())) {
            JOptionPane.showMessageDialog(this, "Failed to save the map for this scenario.", SAVE_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        CampaignManager.ScenarioDefinition scenarioDefinition = buildScenarioDefinition(mapFile.getAbsolutePath());
        if (!campaignManager.saveScenarioDefinition(scenarioFile.getAbsolutePath(), scenarioDefinition)) {
            JOptionPane.showMessageDialog(this, "Failed to save scenario.", SAVE_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        currentMapFile = mapFile;
        currentScenarioFile = scenarioFile;
        statusLabel.setText("Saved scenario to " + scenarioFile.getAbsolutePath());
    }

    private void playScenario() {
        onPlayScenario.accept(tileMap,
                buildScenarioDefinition(currentMapFile != null ? currentMapFile.getAbsolutePath() : ""));
    }

    private CampaignManager.ScenarioDefinition buildScenarioDefinition(String mapPath) {
        return new CampaignManager.ScenarioDefinition(nameField.getText().trim(), nameField.getText().trim(),
                (String)battlefieldTypeBox.getSelectedItem(), historicalNotesArea.getText(), mapPath,
                new ArrayList<>(forcePlacements), new LinkedHashMap<>(startingFood), new LinkedHashMap<>(aiDirectives));
    }

    private String resolveBattleName() {
        String name = nameField.getText().trim();
        return name.isBlank() ? "Untitled Battle" : name;
    }

    private File resolveMapFile(String mapPath, File scenarioFile) {
        if (mapPath == null || mapPath.isBlank()) {
            return null;
        }
        File directFile = new File(mapPath);
        if (directFile.isAbsolute()) {
            return directFile;
        }
        File parent = scenarioFile.getParentFile();
        return parent != null ? new File(parent, mapPath) : directFile;
    }

    private TileMap loadScenarioMap(String mapPath, File scenarioFile) {
        TileMap resourceMap = loadResourceMap(mapPath);
        if (resourceMap != null) {
            return resourceMap;
        }
        File mapFile = resolveMapFile(mapPath, scenarioFile);
        return mapFile != null ? TileMap.fromFile(mapFile) : null;
    }

    private TileMap loadResourceMap(String mapPath) {
        if (mapPath == null || mapPath.isBlank() || !mapPath.startsWith("/")) {
            return null;
        }
        try {
            return new TileMap(mapPath);
        } catch (IOException _) {
            return null;
        }
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private void updateCanvasState() {
        int width = tileMap.getWidth() * EDITOR_TILE_SIZE;
        int height = tileMap.getHeight() * EDITOR_TILE_SIZE;
        mapCanvas.setPreferredSize(new Dimension(width, height));
        mapCanvas.revalidate();
        mapCanvas.repaint();
        updateStatus();
    }

    private void updateStatus() {
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(59, 46, 35));
        statusLabel.setForeground(new Color(240, 228, 204));
        statusLabel.setFont(new Font(SANS_SERIF, Font.PLAIN, 13));
        statusLabel.setText("Battle: " + tileMap.getBattleName() + "    Map: " + tileMap.getWidth() + "x"
                + tileMap.getHeight() + "    Terrain: " + selectedTerrain.name() + "    Structure: "
                + selectedStructure.name() + "    Units: " + forcePlacements.size());
    }

    private final class MapCanvas extends JPanel {
        private final BufferedImage[] terrainTileCache;
        private int lastPaintedCol = Integer.MIN_VALUE;
        private int lastPaintedRow = Integer.MIN_VALUE;

        private MapCanvas() {
            setBackground(new Color(47, 39, 31));
            setDoubleBuffered(true);
            terrainTileCache = createTerrainTileCache();
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    resetDragTracking();
                    handleEditorInteraction(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!isUnitPlacementMode()) {
                        handleEditorInteraction(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) { resetDragTracking(); }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        private void resetDragTracking() {
            lastPaintedCol = Integer.MIN_VALUE;
            lastPaintedRow = Integer.MIN_VALUE;
        }

        private boolean isUnitPlacementMode() { return unitModeButton.isSelected(); }

        private boolean isStructurePlacementMode() { return structureModeButton.isSelected(); }

        private int editorToWorldX(int col) { return col * GamePanel.TILE_SIZE + GamePanel.TILE_SIZE / 2; }

        private int editorToWorldY(int row) { return row * GamePanel.TILE_SIZE + GamePanel.TILE_SIZE / 2; }

        private int worldToEditorX(int worldX) {
            return (int)Math.round(worldX / (double)GamePanel.TILE_SIZE * EDITOR_TILE_SIZE);
        }

        private int worldToEditorY(int worldY) {
            return (int)Math.round(worldY / (double)GamePanel.TILE_SIZE * EDITOR_TILE_SIZE);
        }

        private void handleEditorInteraction(MouseEvent e) {
            if (isUnitPlacementMode()) {
                if (e.getButton() == MouseEvent.BUTTON3 || SwingUtilities.isRightMouseButton(e)) {
                    removeNearestPlacement(e.getPoint());
                } else {
                    placeUnit(e.getPoint());
                }
                return;
            }
            if (isStructurePlacementMode()) {
                paintStructure(e.getPoint(),
                        e.getButton() == MouseEvent.BUTTON3 || SwingUtilities.isRightMouseButton(e));
                return;
            }
            paintTerrain(e.getPoint());
        }

        private void paintTerrain(Point point) {
            int col = point.x / EDITOR_TILE_SIZE;
            int row = point.y / EDITOR_TILE_SIZE;
            if (col < 0 || row < 0 || col >= tileMap.getWidth() || row >= tileMap.getHeight()) {
                return;
            }
            if (col == lastPaintedCol && row == lastPaintedRow) {
                return;
            }
            if (tileMap.getTerrainType(col, row) == selectedTerrain) {
                lastPaintedCol = col;
                lastPaintedRow = row;
                return;
            }
            tileMap.setTerrainType(col, row, selectedTerrain);
            lastPaintedCol = col;
            lastPaintedRow = row;
            repaintTile(col, row);
        }

        private void placeUnit(Point point) {
            int col = point.x / EDITOR_TILE_SIZE;
            int row = point.y / EDITOR_TILE_SIZE;
            if (col < 0 || row < 0 || col >= tileMap.getWidth() || row >= tileMap.getHeight()) {
                return;
            }
            CampaignManager.ForcePlacement placement = new CampaignManager.ForcePlacement(
                    (Unit.SoldierCulture)cultureBox.getSelectedItem(), (Unit.SoldierType)typeBox.getSelectedItem(),
                    editorToWorldX(col), editorToWorldY(row), (Integer)sizeBox.getSelectedItem(),
                    (Integer)experienceSpinner.getValue());
            removeNearestPlacement(point);
            forcePlacements.add(placement);
            repaintPlacement(placement);
            updateStatus();
        }

        private void paintStructure(Point point, boolean erase) {
            int col = point.x / EDITOR_TILE_SIZE;
            int row = point.y / EDITOR_TILE_SIZE;
            if (col < 0 || row < 0 || col >= tileMap.getWidth() || row >= tileMap.getHeight()) {
                return;
            }
            StructureType targetStructure = erase ? StructureType.NONE : selectedStructure;
            if (!erase && !tileMap.canPlaceStructure(col, row, targetStructure)) {
                return;
            }
            if (tileMap.getStructureType(col, row) == targetStructure) {
                return;
            }
            tileMap.setStructureType(col, row, targetStructure);
            repaintTile(col, row);
        }

        private void removeNearestPlacement(Point point) {
            CampaignManager.ForcePlacement closest = null;
            double bestDistance = Double.MAX_VALUE;
            for (CampaignManager.ForcePlacement placement : forcePlacements) {
                double distance = point.distance(worldToEditorX(placement.getX()), worldToEditorY(placement.getY()));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    closest = placement;
                }
            }
            if (closest != null && bestDistance <= EDITOR_TILE_SIZE * 0.75) {
                forcePlacements.remove(closest);
                repaintPlacement(closest);
                updateStatus();
            }
        }

        private void repaintTile(int col, int row) {
            repaint(col * EDITOR_TILE_SIZE, row * EDITOR_TILE_SIZE, EDITOR_TILE_SIZE + 1, EDITOR_TILE_SIZE + 1);
        }

        private void repaintPlacement(CampaignManager.ForcePlacement placement) {
            Rectangle bounds = getPlacementBounds(placement);
            repaint(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4);
        }

        private Rectangle getPlacementBounds(CampaignManager.ForcePlacement placement) {
            int centerX = worldToEditorX(placement.getX());
            int centerY = worldToEditorY(placement.getY());
            int width = Math.max(12, (int)Math.round((placement.getSize() / 5000.0) * 22.0 + 10.0));
            int height = Math.max(8, width / 2);
            return new Rectangle(centerX - width / 2, centerY - height / 2, width, height);
        }

        private BufferedImage[] createTerrainTileCache() {
            BufferedImage[] cache = new BufferedImage[TerrainType.values().length];
            for (TerrainType terrainType : TerrainType.values()) {
                cache[terrainType.ordinal()] = createTerrainTileImage(terrainType);
            }
            return cache;
        }

        private BufferedImage createTerrainTileImage(TerrainType terrainType) {
            GraphicsConfiguration configuration = getGraphicsConfiguration();
            BufferedImage image = configuration != null
                    ? configuration.createCompatibleImage(EDITOR_TILE_SIZE, EDITOR_TILE_SIZE)
                    : new BufferedImage(EDITOR_TILE_SIZE, EDITOR_TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tileGraphics = image.createGraphics();
            try {
                drawTerrainTile(tileGraphics, terrainType, 0, 0);
            } finally {
                tileGraphics.dispose();
            }
            return image;
        }

        private void drawTerrainTile(Graphics2D g2d, TerrainType terrainType, int x, int y) {
            Color baseColor = switch (terrainType) {
            case FLAT_GRASS -> new Color(126, 168, 88);
            case HILLY_GRASS -> new Color(108, 145, 74);
            case IMPASSABLE_MOUNTAIN -> new Color(95, 99, 110);
            case MOUNTAIN_PASS -> new Color(145, 126, 92);
            case RIVER -> new Color(68, 116, 170);
            case FLAT_FOREST -> new Color(77, 118, 58);
            case HILLY_FOREST -> new Color(67, 99, 50);
            case MOUNTAINOUS_FOREST -> new Color(72, 86, 63);
            case MARSHLAND -> new Color(105, 118, 78);
            case FORESTED_MARSHLAND -> new Color(86, 99, 64);
            case FLAT_MUD -> new Color(131, 102, 74);
            case FORESTED_MUD -> new Color(100, 88, 63);
            case HILLY_MUD -> new Color(120, 96, 70);
            };
            g2d.setColor(baseColor);
            g2d.fillRect(x, y, EDITOR_TILE_SIZE, EDITOR_TILE_SIZE);
        }

        private void drawForcePlacement(Graphics2D g2d, CampaignManager.ForcePlacement placement) {
            int centerX = worldToEditorX(placement.getX());
            int centerY = worldToEditorY(placement.getY());
            int width = Math.max(12, (int)Math.round((placement.getSize() / 5000.0) * 22.0 + 10.0));
            int height = Math.max(8, width / 2);
            Color fillColor = switch (placement.getCulture()) {
            case ROMAN -> new Color(150, 0, 0);
            case GALLIC -> new Color(0, 100, 0);
            case PARTHIAN -> Color.BLUE;
            case EGYPTIAN -> Color.YELLOW.darker();
            case BRITON -> new Color(96, 72, 24);
            case POMPEIAN -> new Color(92, 92, 132);
            };
            g2d.setColor(fillColor);
            g2d.fillRect(centerX - width / 2, centerY - height / 2, width, height);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(centerX - width / 2, centerY - height / 2, width, height);
            String label = switch (placement.getType()) {
            case ROMAN_ARCHER, GALLIC_ARCHER, PARTHIAN_ARCHER, EGYPTIAN_ARCHER -> "A";
            case PARTHIAN_HORSE_ARCHER -> "HA";
            case ROMAN_CAVALRY, GALLIC_CAVALRY, PARTHIAN_CAVALRY, EGYPTIAN_CAVALRY, EGYPTIAN_CHARIOT -> "C";
            case ROMAN_SPEARMAN -> "S";
            default -> "I";
            };
            g2d.setFont(new Font(SANS_SERIF, Font.BOLD, 10));
            g2d.drawString(label, centerX - 5, centerY + 4);
        }

        private void drawStructureTile(Graphics2D g2d, int col, int row, StructureType structureType, int x, int y) {
            if (structureType == null || structureType == StructureType.NONE) {
                return;
            }
            switch (structureType) {
            case BRIDGE -> drawBridge(g2d, col, row, x, y);
            case FORT -> drawFort(g2d, x, y);
            case WALL -> drawWall(g2d, col, row, x, y);
            case NONE -> {
                // nothing
            }
            }
        }

        private void drawBridge(Graphics2D g2d, int col, int row, int x, int y) {
            boolean horizontalDeck = tileMap == null || tileMap.isBridgeDeckHorizontal(col, row);
            boolean linkWest = horizontalDeck && tileMap != null && tileMap.getStructureType(col - 1, row).isBridge();
            boolean linkEast = horizontalDeck && tileMap != null && tileMap.getStructureType(col + 1, row).isBridge();
            boolean linkNorth = !horizontalDeck && tileMap != null && tileMap.getStructureType(col, row - 1).isBridge();
            boolean linkSouth = !horizontalDeck && tileMap != null && tileMap.getStructureType(col, row + 1).isBridge();
            if (horizontalDeck) {
                StructureRenderHelper.drawBridge(g2d, x, y, EDITOR_TILE_SIZE, true, linkWest, linkEast);
            } else {
                StructureRenderHelper.drawBridge(g2d, x, y, EDITOR_TILE_SIZE, false, linkNorth, linkSouth);
            }
        }

        private void drawFort(Graphics2D g2d, int x, int y) {
            StructureRenderHelper.drawFort(g2d, x, y, EDITOR_TILE_SIZE);
        }

        private void drawWall(Graphics2D g2d, int col, int row, int x, int y) {
            boolean north = hasWallNeighbor(col, row - 1);
            boolean south = hasWallNeighbor(col, row + 1);
            boolean west = hasWallNeighbor(col - 1, row);
            boolean east = hasWallNeighbor(col + 1, row);
            StructureRenderHelper.drawWall(g2d, x, y, EDITOR_TILE_SIZE, north, south, west, east);
        }

        private boolean hasWallNeighbor(int col, int row) {
            return tileMap != null && tileMap.getStructureType(col, row).isWall();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D)g;
            Rectangle clip = g2d.getClipBounds();
            int startCol = Math.max(0, clip.x / EDITOR_TILE_SIZE);
            int endCol = Math.min(tileMap.getWidth() - 1, (clip.x + clip.width) / EDITOR_TILE_SIZE);
            int startRow = Math.max(0, clip.y / EDITOR_TILE_SIZE);
            int endRow = Math.min(tileMap.getHeight() - 1, (clip.y + clip.height) / EDITOR_TILE_SIZE);
            for (int row = startRow; row <= endRow; row++) {
                for (int col = startCol; col <= endCol; col++) {
                    TerrainType terrainType = tileMap.getTerrainType(col, row);
                    int tileX = col * EDITOR_TILE_SIZE;
                    int tileY = row * EDITOR_TILE_SIZE;
                    g2d.drawImage(terrainTileCache[terrainType.ordinal()], tileX, tileY, null);
                    drawStructureTile(g2d, col, row, tileMap.getStructureType(col, row), tileX, tileY);
                }
            }
            for (CampaignManager.ForcePlacement placement : forcePlacements) {
                if (clip.intersects(getPlacementBounds(placement))) {
                    drawForcePlacement(g2d, placement);
                }
            }
        }
    }
}