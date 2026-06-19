package main;

import campaign.CampaignManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import persistence.UserProfile;
import persistence.UserProfileStore;
import units.Unit;
import world.TileMap;

public class Main {
    private static final String LOAD_ERROR = "Load Error";
    private static final String RESOURCES = "resources";
    private static final String SCENARIOS = "scenarios";
    private static final String CAESAR = "caesar";
    private static final String IN_BRITAIN = "Caesar in Britain";
    private static final String LAUNCH_BATTLE = "Launch Battle";
    private static final Unit.SoldierCulture PLAYER_CULTURE = Unit.SoldierCulture.ROMAN;
    private static final List<ArmyPurchaseOption> ARMY_PURCHASE_OPTIONS = List.of(
            new ArmyPurchaseOption("Auxilia Light Infantry", Unit.SoldierType.ROMAN_LIGHT_INFANTRY, 1000, 2, 2),
            new ArmyPurchaseOption("Roman Archers", Unit.SoldierType.ROMAN_ARCHER, 1000, 2, 3),
            new ArmyPurchaseOption("Legionaries", Unit.SoldierType.ROMAN_HEAVY_INFANTRY, 2000, 3, 5),
            new ArmyPurchaseOption("Veteran Legionaries", Unit.SoldierType.ROMAN_HEAVY_INFANTRY, 2000, 5, 7),
            new ArmyPurchaseOption("Roman Cavalry", Unit.SoldierType.ROMAN_CAVALRY, 1000, 4, 6));
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Main.class.getName());
    private static UserProfile userProfile = UserProfile.defaults();

    private static void launchApplication(String[] args) {
        boolean launchedWithArgs = args.length > 0;
        userProfile = UserProfileStore.load();
        JFrame window = createWindow(launchedWithArgs);
        showStartMenu(window);
        window.setVisible(true);
    }

    private static JFrame createWindow(boolean launchedWithArgs) {
        JFrame window = new JFrame();
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setLayout(new BorderLayout());
        window.setResizable(true);
        window.setTitle("Roman Republic RTS");
        if (launchedWithArgs) {
            window.setName("RomanRepublicRTS");
        }
        configureInitialWindow(window);
        return window;
    }

    private static void configureInitialWindow(JFrame window) {
        GraphicsConfiguration graphicsConfiguration = window.getGraphicsConfiguration();
        if (graphicsConfiguration == null || !userProfile.launchFullscreen()) {
            window.setUndecorated(false);
            window.setSize(960, 720);
            window.setLocationRelativeTo(null);
            return;
        }
        window.setUndecorated(true);
        window.setBounds(graphicsConfiguration.getBounds());
    }

    private static boolean isBorderlessFullscreen(JFrame window) {
        GraphicsConfiguration graphicsConfiguration = window.getGraphicsConfiguration();
        if (graphicsConfiguration == null) {
            return false;
        }
        Rectangle screenBounds = graphicsConfiguration.getBounds();
        return window.isUndecorated() && screenBounds.equals(window.getBounds());
    }

    private static void showStartMenu(JFrame window) {
        StartMenuPanel startMenuPanel = new StartMenuPanel(() -> showCampaign(window), () -> showSandbox(window),
                () -> showMapEditor(window), () -> showSettings(window), userProfile.coins());
        swapContent(window, startMenuPanel, true);
    }

    private static void showCampaign(JFrame window) {
        Object[] options = {"Caesar Against Vercingetorix", IN_BRITAIN, "Caesar's Civil War", "Sample Campaign",
                "Load Scenario File", "Back"};
        int choice = JOptionPane.showOptionDialog(window, "Choose a campaign to launch.", "Play Campaign",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            showGaulCampaign(window);
            return;
        }
        if (choice == 1) {
            showBritainCampaign(window);
            return;
        }
        if (choice == 2) {
            showCivilWarCampaign(window);
            return;
        }
        if (choice == 3) {
            launchSampleCampaign(window);
            return;
        }
        if (choice == 4) {
            launchScenarioFromFile(window);
        }
    }

    private static List<String> findCaesarResourcePaths() {
        List<String> resourcePaths = new ArrayList<>();
        Path dir = Path.of(System.getProperty("user.dir"), RESOURCES, SCENARIOS, CAESAR);
        if (Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".csv")).sorted().forEach(p -> resourcePaths
                        .add("/" + RESOURCES + "/" + SCENARIOS + "/" + CAESAR + "/" + p.getFileName().toString()));
            } catch (IOException ignored) {
                resourcePaths.clear();
                LOGGER.log(java.util.logging.Level.FINE, "Ignored exception while listing Caesar resources", ignored);
            }
        }
        return resourcePaths;
    }

    private static List<String> buildScenarioLabels(CampaignManager campaignManager, List<String> resourcePaths) {
        List<String> labels = new ArrayList<>();
        for (String rp : resourcePaths) {
            try {
                CampaignManager.ScenarioDefinition sd = campaignManager.loadScenarioDefinition(rp);
                if (sd != null) {
                    String label = sd.getHistoricalBattle() != null && !sd.getHistoricalBattle().isBlank()
                            ? sd.getHistoricalBattle()
                            : sd.getName();
                    labels.add(label);
                } else {
                    labels.add(rp);
                }
            } catch (Exception ignored) {
                labels.add(rp);
                LOGGER.log(java.util.logging.Level.FINE, "Ignored exception while building scenario label", ignored);
            }
        }
        return labels;
    }

    private static void showGaulCampaign(JFrame window) {
        Object[] options = {"Avaricum", "Gergovia", "Alesia", "Back"};
        int choice = JOptionPane.showOptionDialog(window, "Choose a battle from Caesar's 52 BC war in Gaul.",
                "Caesar Against Vercingetorix", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options,
                options[0]);
        if (choice == 0) {
            launchBuiltInScenario(window, getAvaricumScenarioPath(), "the Battle of Avaricum");
            return;
        }
        if (choice == 1) {
            launchBuiltInScenario(window, getGergoviaScenarioPath(), "the Battle of Gergovia");
            return;
        }
        if (choice == 2) {
            launchBuiltInScenario(window, getAlesiaScenarioPath(), "the Battle of Alesia");
        }
    }

    private static List<String> filterResourcePathsByHistoricalYear(CampaignManager campaignManager,
            List<String> resourcePaths, String... yearTokens) {
        List<String> filtered = new ArrayList<>();
        if (resourcePaths == null || resourcePaths.isEmpty()) {
            return filtered;
        }
        for (String rp : resourcePaths) {
            try {
                CampaignManager.ScenarioDefinition sd = campaignManager.loadScenarioDefinition(rp);
                if (sd == null) {
                    continue;
                }
                String hist = sd.getHistoricalBattle();
                if (hist == null || hist.isBlank()) {
                    continue;
                }
                for (String token : yearTokens) {
                    if (hist.contains(token)) {
                        filtered.add(rp);
                        break;
                    }
                }
            } catch (Exception ignored) {
                // ignore malformed or unreadable scenario
                LOGGER.log(java.util.logging.Level.FINE, "Ignored exception while filtering scenario by year", ignored);
            }
        }
        return filtered;
    }

    private static void showBritainCampaign(JFrame window) {
        CampaignManager campaignManager = new CampaignManager();
        List<String> resourcePaths = findCaesarResourcePaths();
        List<String> britainPaths = filterResourcePathsByHistoricalYear(campaignManager, resourcePaths, "55 BC",
                "54 BC");
        if (britainPaths.isEmpty()) {
            Object[] options = {"Kent Landing", "Thames Crossing", "Back"};
            int choice = JOptionPane.showOptionDialog(window, "Choose a battle from Caesar's expeditions to Britain.",
                    IN_BRITAIN, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                launchBuiltInScenario(window, getKentLandingScenarioPath(), "the Kent landing");
                return;
            }
            if (choice == 1) {
                launchBuiltInScenario(window, getThamesCrossingScenarioPath(), "the Thames crossing");
            }
            return;
        }
        List<String> labels = buildScenarioLabels(campaignManager, britainPaths);
        labels.add("Back");
        Object[] options = labels.toArray();
        int choice = JOptionPane.showOptionDialog(window, "Choose a battle from Caesar's expeditions to Britain.",
                IN_BRITAIN, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice < 0 || choice >= britainPaths.size()) {
            return;
        }
        launchBuiltInScenario(window, britainPaths.get(choice), "the " + labels.get(choice));
    }

    private static void showCivilWarCampaign(JFrame window) {
        CampaignManager campaignManager = new CampaignManager();
        List<String> resourcePaths = findCaesarResourcePaths();
        List<String> civilWarPaths = filterResourcePathsByHistoricalYear(campaignManager, resourcePaths, "49 BC",
                "48 BC", "47 BC", "46 BC", "45 BC");
        List<String> labels = buildScenarioLabels(campaignManager, civilWarPaths);
        labels.add("Back");
        Object[] options = labels.toArray();
        int choice = JOptionPane.showOptionDialog(window, "Choose a battle from Caesar's civil war.",
                "Caesar's Civil War", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice < 0 || choice >= civilWarPaths.size()) {
            return;
        }
        launchBuiltInScenario(window, civilWarPaths.get(choice), "the " + labels.get(choice));
    }

    private static void showSandbox(JFrame window) {
        Object[] options = {"Sample Map", "Load Map File", "Back"};
        int choice = JOptionPane.showOptionDialog(window, "Choose a map to play in sandbox mode.", "Play Sandbox",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            launchSampleSandbox(window);
            return;
        }
        if (choice == 1) {
            launchMapSandboxFromFile(window);
        }
    }

    private static void showMapEditor(JFrame window) {
        MapEditorPanel mapEditorPanel = new MapEditorPanel(() -> showStartMenu(window),
                (tileMap, scenarioDefinition) -> launchGame(window, tileMap, scenarioDefinition));
        swapContent(window, mapEditorPanel, true);
    }

    private static void showSettings(JFrame window) {
        JCheckBox fullscreen = new JCheckBox("Launch fullscreen on startup", userProfile.launchFullscreen());
        JCheckBox briefings = new JCheckBox("Show scenario briefings", userProfile.showScenarioBriefings());
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(fullscreen);
        panel.add(briefings);
        int result = JOptionPane.showConfirmDialog(window, panel, "Settings", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            userProfile = userProfile.withLaunchFullscreen(fullscreen.isSelected())
                    .withShowScenarioBriefings(briefings.isSelected());
            UserProfileStore.save(userProfile);
        }
        showStartMenu(window);
    }

    private static void launchSampleSandbox(JFrame window) {
        TileMap tileMap = loadResourceMap(getSampleMapPath());
        if (tileMap == null) {
            JOptionPane.showMessageDialog(window, "Failed to load the built-in sample map.", LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        launchSandbox(window, tileMap, "Sample Sandbox");
    }

    private static void launchMapSandboxFromFile(JFrame window) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Map");
        chooser.setFileFilter(new FileNameExtensionFilter("Map CSV (*.csv)", "csv"));
        int result = chooser.showOpenDialog(window);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        TileMap tileMap = TileMap.fromFile(chooser.getSelectedFile());
        if (tileMap == null) {
            JOptionPane.showMessageDialog(window, "Failed to load map file.", LOAD_ERROR, JOptionPane.ERROR_MESSAGE);
            return;
        }
        launchSandbox(window, tileMap, chooser.getSelectedFile().getName());
    }

    private static void launchScenarioFromFile(JFrame window) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Scenario");
        chooser.setFileFilter(new FileNameExtensionFilter("Scenario CSV (*.csv)", "csv"));
        int result = chooser.showOpenDialog(window);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File scenarioFile = chooser.getSelectedFile();
        CampaignManager campaignManager = new CampaignManager();
        CampaignManager.ScenarioDefinition scenarioDefinition = campaignManager.loadScenarioDefinition(scenarioFile);
        if (scenarioDefinition == null) {
            JOptionPane.showMessageDialog(window, "Failed to load scenario file.", LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!showScenarioBriefing(window, scenarioDefinition)) {
            return;
        }
        TileMap tileMap = loadScenarioTileMap(scenarioDefinition, scenarioFile);
        if (tileMap == null) {
            JOptionPane.showMessageDialog(window, "Failed to load the scenario map.", LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        launchScenarioMatch(window, tileMap, scenarioDefinition, MatchMode.CAMPAIGN);
    }

    private static void launchSampleCampaign(JFrame window) {
        launchBuiltInScenario(window, getSampleScenarioPath(), "the built-in sample scenario");
    }

    private static void launchBuiltInScenario(JFrame window, String scenarioPath, String displayName) {
        CampaignManager campaignManager = new CampaignManager();
        CampaignManager.ScenarioDefinition scenarioDefinition = campaignManager.loadScenarioDefinition(scenarioPath);
        if (scenarioDefinition == null) {
            JOptionPane.showMessageDialog(window, "Failed to load " + displayName + '.', LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!showScenarioBriefing(window, scenarioDefinition)) {
            return;
        }
        TileMap tileMap = loadScenarioTileMap(scenarioDefinition, null);
        if (tileMap == null) {
            JOptionPane.showMessageDialog(window, "Failed to load the map for " + displayName + '.', LOAD_ERROR,
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        launchScenarioMatch(window, tileMap, scenarioDefinition, MatchMode.CAMPAIGN);
    }

    private static boolean showScenarioBriefing(JFrame window, CampaignManager.ScenarioDefinition scenarioDefinition) {
        if (scenarioDefinition == null || !userProfile.showScenarioBriefings()) {
            return true;
        }
        JTextArea briefingText = new JTextArea(buildScenarioBriefing(scenarioDefinition));
        briefingText.setEditable(false);
        briefingText.setLineWrap(true);
        briefingText.setWrapStyleWord(true);
        briefingText.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(briefingText);
        scrollPane.setPreferredSize(new Dimension(620, 360));
        String title = scenarioDefinition.getHistoricalBattle() != null
                && !scenarioDefinition.getHistoricalBattle().isBlank() ? scenarioDefinition.getHistoricalBattle()
                        : scenarioDefinition.getName();
        Object[] options = {LAUNCH_BATTLE, "Back"};
        int choice = JOptionPane.showOptionDialog(window, scrollPane, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        return choice == JOptionPane.OK_OPTION;
    }

    private static String buildScenarioBriefing(CampaignManager.ScenarioDefinition scenarioDefinition) {
        StringBuilder builder = new StringBuilder();
        appendScenarioOverview(builder, scenarioDefinition);
        appendForceRepresentation(builder, scenarioDefinition);
        appendOpeningPlans(builder, scenarioDefinition.getAiDirectives());
        return builder.toString().trim();
    }

    private static void appendScenarioOverview(StringBuilder builder,
            CampaignManager.ScenarioDefinition scenarioDefinition) {
        appendParagraph(builder, scenarioDefinition.getName());
        appendParagraph(builder, scenarioDefinition.getHistoricalNotes());
        if (scenarioDefinition.getBattlefieldType() != null && !scenarioDefinition.getBattlefieldType().isBlank()) {
            appendParagraph(builder, "Battle type: " + humanizeToken(scenarioDefinition.getBattlefieldType()));
        }
    }

    private static void appendForceRepresentation(StringBuilder builder,
            CampaignManager.ScenarioDefinition scenarioDefinition) {
        Map<Unit.SoldierCulture, ForceSummary> forceSummaries = summarizeForces(scenarioDefinition);
        if (forceSummaries.isEmpty()) {
            return;
        }
        builder.append("Force representation:\n");
        for (Map.Entry<Unit.SoldierCulture, ForceSummary> entry : forceSummaries.entrySet()) {
            ForceSummary summary = entry.getValue();
            builder.append("- ").append(humanizeToken(entry.getKey().name())).append(": ~")
                    .append(String.format("%,d", summary.manpower())).append(" troops across ")
                    .append(summary.formations()).append(summary.formations() == 1 ? " formation" : " formations")
                    .append(".\n");
        }
        builder.append('\n');
    }

    private static void appendOpeningPlans(StringBuilder builder,
            Map<Unit.SoldierCulture, CampaignManager.AIDirective> directives) {
        if (directives.isEmpty()) {
            return;
        }
        builder.append("Opening plans:\n");
        for (Map.Entry<Unit.SoldierCulture, CampaignManager.AIDirective> entry : directives.entrySet()) {
            builder.append("- ").append(humanizeToken(entry.getKey().name())).append(": ");
            appendPlanLine(builder, entry.getValue());
            builder.append('\n');
        }
    }

    private static void appendPlanLine(StringBuilder builder, CampaignManager.AIDirective directive) {
        appendSentence(builder, directive.getHistoricalStyle());
        if (directive.getHistoricalGoal() == null || directive.getHistoricalGoal().isBlank()) {
            return;
        }
        if (builder.charAt(builder.length() - 1) != ' ' && builder.charAt(builder.length() - 1) != ':') {
            builder.append(' ');
        }
        builder.append("Objective: ");
        appendSentence(builder, directive.getHistoricalGoal());
    }

    private static void appendParagraph(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        builder.append(text.trim()).append("\n\n");
    }

    private static Map<Unit.SoldierCulture, ForceSummary> summarizeForces(
            CampaignManager.ScenarioDefinition scenarioDefinition) {
        Map<Unit.SoldierCulture, ForceSummary> summaries = new LinkedHashMap<>();
        for (CampaignManager.ForcePlacement forcePlacement : scenarioDefinition.getForces()) {
            ForceSummary existing = summaries.get(forcePlacement.getCulture());
            int formations = existing != null ? existing.formations() : 0;
            int manpower = existing != null ? existing.manpower() : 0;
            summaries.put(forcePlacement.getCulture(),
                    new ForceSummary(formations + 1, manpower + Math.max(0, forcePlacement.getSize())));
        }
        return summaries;
    }

    private static void appendSentence(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String trimmed = text.trim();
        builder.append(Character.toUpperCase(trimmed.charAt(0)));
        if (trimmed.length() > 1) {
            builder.append(trimmed.substring(1));
        }
        char lastCharacter = trimmed.charAt(trimmed.length() - 1);
        if (lastCharacter != '.' && lastCharacter != '!' && lastCharacter != '?') {
            builder.append('.');
        }
    }

    private static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : token.trim().replace('-', '_').toLowerCase().split("_+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private static TileMap loadScenarioTileMap(CampaignManager.ScenarioDefinition scenarioDefinition,
            File scenarioFile) {
        if (scenarioDefinition == null || scenarioDefinition.getMapPath() == null) {
            return null;
        }
        TileMap resourceMap = loadResourceMap(scenarioDefinition.getMapPath());
        if (resourceMap != null) {
            return resourceMap;
        }
        File mapFile = resolveScenarioMapFile(scenarioDefinition.getMapPath(), scenarioFile);
        if (mapFile != null && mapFile.isFile()) {
            return TileMap.fromFile(mapFile);
        }
        return null;
    }

    private static File resolveScenarioMapFile(String mapPath, File scenarioFile) {
        if (mapPath == null || mapPath.isBlank()) {
            return null;
        }
        File directFile = new File(mapPath);
        if (directFile.isAbsolute()) {
            return directFile;
        }
        File scenarioDirectory = scenarioFile != null ? scenarioFile.getParentFile() : null;
        return scenarioDirectory != null ? new File(scenarioDirectory, mapPath) : directFile;
    }

    private static TileMap loadResourceMap(String mapPath) {
        if (mapPath == null || mapPath.isBlank() || !mapPath.startsWith("/")) {
            return null;
        }
        try {
            return new TileMap(mapPath);
        } catch (IOException _) {
            return null;
        }
    }

    private static String getSampleScenarioPath() {
        return System.getProperty("romanrts.sampleScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, "sample_battle.csv"));
    }

    private static String getAvaricumScenarioPath() {
        return System.getProperty("romanrts.caesar.avaricumScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, CAESAR, "avaricum.csv"));
    }

    private static String getGergoviaScenarioPath() {
        return System.getProperty("romanrts.caesar.gergoviaScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, CAESAR, "gergovia.csv"));
    }

    private static String getAlesiaScenarioPath() {
        return System.getProperty("romanrts.caesar.alesiaScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, CAESAR, "alesia.csv"));
    }

    private static String getKentLandingScenarioPath() {
        return System.getProperty("romanrts.caesar.britain.kentLandingScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, CAESAR, "kent_landing.csv"));
    }

    private static String getThamesCrossingScenarioPath() {
        return System.getProperty("romanrts.caesar.britain.thamesCrossingScenario",
                buildBundledResourcePath(RESOURCES, SCENARIOS, CAESAR, "thames_crossing.csv"));
    }

    private static String getSampleMapPath() {
        return System.getProperty("romanrts.sampleMap", buildBundledResourcePath(RESOURCES, "maps", "sample_map.csv"));
    }

    private static String buildBundledResourcePath(String firstSegment, String... remainingSegments) {
        return '/' + Path.of(firstSegment, remainingSegments).toString().replace(File.separatorChar, '/');
    }

    private static void launchGame(JFrame window, TileMap tileMap,
            CampaignManager.ScenarioDefinition scenarioDefinition) {
        launchGame(window, tileMap, scenarioDefinition, MatchMode.CUSTOM,
                resolveBattleName(scenarioDefinition, tileMap, "Custom Battle"));
    }

    private static void launchSandbox(JFrame window, TileMap tileMap, String battleName) {
        CampaignManager.ScenarioDefinition sandboxScenario = createSandboxScenario(battleName);
        launchScenarioMatch(window, tileMap, sandboxScenario, MatchMode.SANDBOX);
    }

    private static void launchScenarioMatch(JFrame window, TileMap tileMap,
            CampaignManager.ScenarioDefinition scenarioDefinition, MatchMode matchMode) {
        CampaignManager.ScenarioDefinition preparedScenario = prepareScenarioForLaunch(window, tileMap,
                scenarioDefinition, PLAYER_CULTURE);
        if (preparedScenario == null) {
            return;
        }
        String battleName = resolveBattleName(preparedScenario, tileMap, preparedScenario.getName());
        launchGame(window, tileMap, preparedScenario, matchMode, battleName);
    }

    private static void launchGame(JFrame window, TileMap tileMap,
            CampaignManager.ScenarioDefinition scenarioDefinition, MatchMode matchMode, String battleName) {
        MatchSession matchSession = createMatchSession(battleName, matchMode, scenarioDefinition, PLAYER_CULTURE);
        GamePanel gamePanel = new GamePanel(tileMap, scenarioDefinition, matchSession,
                result -> handleMatchResolution(window, result), () -> showStartMenu(window));
        swapContent(window, gamePanel, false);
        gamePanel.requestFocusInWindow();
        gamePanel.startGameThread();
    }

    private static void handleMatchResolution(JFrame window, MatchResult result) {
        if (result == null) {
            showStartMenu(window);
            return;
        }
        if (result.victory() && result.rewardCoins() > 0) {
            userProfile = userProfile.withCoins(userProfile.coins() + result.rewardCoins());
            UserProfileStore.save(userProfile);
        }
        String message;
        if (result.victory()) {
            message = "Victory in " + result.battleName() + '!';
            if (result.rewardCoins() > 0) {
                message += "\nCoins earned: " + result.rewardCoins() + "\nTotal coins: " + userProfile.coins();
            }
        } else {
            message = "Defeat in " + result.battleName() + '.';
        }
        JOptionPane.showMessageDialog(window, message, result.victory() ? "Victory" : "Defeat",
                JOptionPane.INFORMATION_MESSAGE);
        showStartMenu(window);
    }

    private static String resolveBattleName(CampaignManager.ScenarioDefinition scenarioDefinition, TileMap tileMap,
            String fallback) {
        if (scenarioDefinition != null && scenarioDefinition.getHistoricalBattle() != null
                && !scenarioDefinition.getHistoricalBattle().isBlank()) {
            return scenarioDefinition.getHistoricalBattle();
        }
        if (scenarioDefinition != null && scenarioDefinition.getName() != null
                && !scenarioDefinition.getName().isBlank()) {
            return scenarioDefinition.getName();
        }
        if (tileMap != null && tileMap.getBattleName() != null && !tileMap.getBattleName().isBlank()) {
            return tileMap.getBattleName();
        }
        return fallback;
    }

    private static CampaignManager.ScenarioDefinition createSandboxScenario(String battleName) {
        return new CampaignManager.ScenarioDefinition(battleName, battleName, "sandbox_skirmish",
                "Open battle with default forces.", "", buildDefaultSandboxForces(), new LinkedHashMap<>(),
                new LinkedHashMap<>());
    }

    private static List<CampaignManager.ForcePlacement> buildDefaultSandboxForces() {
        return List.of(
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.ROMAN, Unit.SoldierType.ROMAN_HEAVY_INFANTRY,
                        300, 200, 5000, 1),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.ROMAN, Unit.SoldierType.ROMAN_LIGHT_INFANTRY,
                        300, 300, 2000, 1),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.ROMAN, Unit.SoldierType.ROMAN_ARCHER, 350, 300,
                        1000, 1),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.ROMAN, Unit.SoldierType.ROMAN_CAVALRY, 400, 400,
                        5000, 3),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.GALLIC, Unit.SoldierType.GALLIC_INFANTRY, 50, 50,
                        5000, 1),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.GALLIC, Unit.SoldierType.GALLIC_CAVALRY, 200,
                        100, 20000, 1),
                new CampaignManager.ForcePlacement(Unit.SoldierCulture.GALLIC, Unit.SoldierType.GALLIC_ARCHER, 300, 100,
                        1000, 1));
    }

    private static CampaignManager.ScenarioDefinition prepareScenarioForLaunch(JFrame window, TileMap tileMap,
            CampaignManager.ScenarioDefinition baseScenario, Unit.SoldierCulture playerCulture) {
        ArmyPurchasePlan purchasePlan = showArmyPurchaseDialog(window, baseScenario, tileMap, playerCulture);
        if (purchasePlan == null) {
            return null;
        }
        if (purchasePlan.spentCoins() > 0) {
            userProfile = userProfile.withCoins(userProfile.coins() - purchasePlan.spentCoins());
            UserProfileStore.save(userProfile);
        }
        return purchasePlan.scenarioDefinition();
    }

    private static ArmyPurchasePlan showArmyPurchaseDialog(JFrame window,
            CampaignManager.ScenarioDefinition baseScenario, TileMap tileMap, Unit.SoldierCulture playerCulture) {
        JSpinner[] quantitySpinners = new JSpinner[ARMY_PURCHASE_OPTIONS.size()];
        JPanel panel = new JPanel(new GridLayout(ARMY_PURCHASE_OPTIONS.size() + 2, 3, 10, 8));
        panel.add(new JLabel("Unit"));
        panel.add(new JLabel("Cost"));
        panel.add(new JLabel("Count"));
        for (int index = 0; index < ARMY_PURCHASE_OPTIONS.size(); index++) {
            ArmyPurchaseOption option = ARMY_PURCHASE_OPTIONS.get(index);
            quantitySpinners[index] = new JSpinner(new SpinnerNumberModel(0, 0, 8, 1));
            panel.add(new JLabel(option.label()));
            panel.add(new JLabel(option.cost() + " coins"));
            panel.add(quantitySpinners[index]);
        }
        while (true) {
            int choice = JOptionPane.showOptionDialog(window, panel, "Armory - Coins Available: " + userProfile.coins(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] {LAUNCH_BATTLE, "Back"},
                    LAUNCH_BATTLE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            int[] quantities = new int[ARMY_PURCHASE_OPTIONS.size()];
            int totalCost = 0;
            for (int index = 0; index < ARMY_PURCHASE_OPTIONS.size(); index++) {
                quantities[index] = (int)quantitySpinners[index].getValue();
                totalCost += quantities[index] * ARMY_PURCHASE_OPTIONS.get(index).cost();
            }
            if (totalCost > userProfile.coins()) {
                JOptionPane.showMessageDialog(window, "You need " + totalCost
                        + " coins for that purchase, but only have " + userProfile.coins() + '.', "Not Enough Coins",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            CampaignManager.ScenarioDefinition purchasedScenario = addPurchasedForces(baseScenario, tileMap,
                    playerCulture, quantities);
            return new ArmyPurchasePlan(purchasedScenario, totalCost);
        }
    }

    private static CampaignManager.ScenarioDefinition addPurchasedForces(
            CampaignManager.ScenarioDefinition baseScenario, TileMap tileMap, Unit.SoldierCulture playerCulture,
            int[] quantities) {
        List<CampaignManager.ForcePlacement> purchasedForces = buildPurchasedForces(baseScenario, tileMap,
                playerCulture, quantities);
        if (purchasedForces.isEmpty()) {
            return baseScenario;
        }
        List<CampaignManager.ForcePlacement> mergedForces = new ArrayList<>(baseScenario.getForces());
        mergedForces.addAll(purchasedForces);
        return new CampaignManager.ScenarioDefinition(baseScenario.getName(), baseScenario.getHistoricalBattle(),
                baseScenario.getBattlefieldType(), baseScenario.getHistoricalNotes(), baseScenario.getMapPath(),
                mergedForces, baseScenario.getStartingFood(), baseScenario.getAiDirectives());
    }

    private static List<CampaignManager.ForcePlacement> buildPurchasedForces(
            CampaignManager.ScenarioDefinition baseScenario, TileMap tileMap, Unit.SoldierCulture playerCulture,
            int[] quantities) {
        List<CampaignManager.ForcePlacement> alliedForces = new ArrayList<>();
        List<CampaignManager.ForcePlacement> enemyForces = new ArrayList<>();
        for (CampaignManager.ForcePlacement forcePlacement : baseScenario.getForces()) {
            if (forcePlacement.getCulture() == playerCulture) {
                alliedForces.add(forcePlacement);
            } else {
                enemyForces.add(forcePlacement);
            }
        }
        Point alliedCenter = computeForceCentroid(alliedForces, tileMap, true);
        Point enemyCenter = computeForceCentroid(enemyForces, tileMap, false);
        double directionX = (double)enemyCenter.x - alliedCenter.x;
        double directionY = (double)enemyCenter.y - alliedCenter.y;
        double length = Math.hypot(directionX, directionY);
        if (length < 1e-6) {
            directionX = 1.0;
            directionY = 0.0;
            length = 1.0;
        }
        double retreatX = -directionX / length;
        double retreatY = -directionY / length;
        double lateralX = -retreatY;
        double lateralY = retreatX;
        Point anchor = clampPointToMap(new Point((int)Math.round(alliedCenter.x + retreatX * GamePanel.TILE_SIZE * 3.0),
                (int)Math.round(alliedCenter.y + retreatY * GamePanel.TILE_SIZE * 3.0)), tileMap);
        List<CampaignManager.ForcePlacement> purchasedForces = new ArrayList<>();
        int placementIndex = 0;
        for (int optionIndex = 0; optionIndex < ARMY_PURCHASE_OPTIONS.size(); optionIndex++) {
            ArmyPurchaseOption option = ARMY_PURCHASE_OPTIONS.get(optionIndex);
            int quantity = optionIndex < quantities.length ? quantities[optionIndex] : 0;
            for (int count = 0; count < quantity; count++) {
                int row = placementIndex / 3;
                int column = placementIndex % 3 - 1;
                double offsetX = anchor.x + lateralX * column * GamePanel.TILE_SIZE * 1.6
                        + retreatX * row * GamePanel.TILE_SIZE * 1.4;
                double offsetY = anchor.y + lateralY * column * GamePanel.TILE_SIZE * 1.6
                        + retreatY * row * GamePanel.TILE_SIZE * 1.4;
                Point spawnPoint = clampPointToMap(new Point((int)Math.round(offsetX), (int)Math.round(offsetY)),
                        tileMap);
                purchasedForces.add(new CampaignManager.ForcePlacement(playerCulture, option.type(), spawnPoint.x,
                        spawnPoint.y, option.size(), option.experienceLevel()));
                placementIndex++;
            }
        }
        return purchasedForces;
    }

    private static Point computeForceCentroid(List<CampaignManager.ForcePlacement> forces, TileMap tileMap,
            boolean leftSideFallback) {
        if (forces.isEmpty()) {
            int fallbackX = leftSideFallback ? GamePanel.TILE_SIZE * 3
                    : tileMap.getWidth() * GamePanel.TILE_SIZE - GamePanel.TILE_SIZE * 3;
            int fallbackY = tileMap.getHeight() * GamePanel.TILE_SIZE / 2;
            return clampPointToMap(new Point(fallbackX, fallbackY), tileMap);
        }
        double sumX = 0.0;
        double sumY = 0.0;
        for (CampaignManager.ForcePlacement forcePlacement : forces) {
            sumX += forcePlacement.getX();
            sumY += forcePlacement.getY();
        }
        return clampPointToMap(new Point((int)Math.round(sumX / forces.size()), (int)Math.round(sumY / forces.size())),
                tileMap);
    }

    private static Point clampPointToMap(Point point, TileMap tileMap) {
        int minX = GamePanel.TILE_SIZE;
        int minY = GamePanel.TILE_SIZE;
        int maxX = Math.max(minX, tileMap.getWidth() * GamePanel.TILE_SIZE - GamePanel.TILE_SIZE);
        int maxY = Math.max(minY, tileMap.getHeight() * GamePanel.TILE_SIZE - GamePanel.TILE_SIZE);
        return new Point(clamp(point.x, minX, maxX), clamp(point.y, minY, maxY));
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static MatchSession createMatchSession(String battleName, MatchMode matchMode,
            CampaignManager.ScenarioDefinition scenarioDefinition, Unit.SoldierCulture playerCulture) {
        int alliedForces = 0;
        int enemyForces = 0;
        for (CampaignManager.ForcePlacement forcePlacement : scenarioDefinition.getForces()) {
            if (forcePlacement.getCulture() == playerCulture) {
                alliedForces++;
            } else {
                enemyForces++;
            }
        }
        return new MatchSession(battleName, matchMode, playerCulture, alliedForces, enemyForces);
    }

    private static void swapContent(JFrame window, JPanel panel, boolean packWindow) {
        window.getContentPane().removeAll();
        window.add(panel, BorderLayout.CENTER);
        if (isBorderlessFullscreen(window)) {
            GraphicsConfiguration graphicsConfiguration = window.getGraphicsConfiguration();
            if (graphicsConfiguration != null) {
                window.setBounds(graphicsConfiguration.getBounds());
            }
        } else if (packWindow) {
            window.pack();
        } else {
            window.setSize(Math.max(window.getWidth(), GamePanel.SCREEN_WIDTH + 16),
                    Math.max(window.getHeight(), GamePanel.SCREEN_HEIGHT + 39));
            window.setLocationRelativeTo(null);
        }
        window.revalidate();
        window.repaint();
    }

    private record ForceSummary(int formations, int manpower) {}

    private record ArmyPurchaseOption(String label, Unit.SoldierType type, int size, int experienceLevel, int cost) {}

    private record ArmyPurchasePlan(CampaignManager.ScenarioDefinition scenarioDefinition, int spentCoins) {}

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> launchApplication(args)); }
}