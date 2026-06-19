package campaign;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import persistence.PersistenceManager;
import units.Unit;

public class CampaignManager {
    private static final String HISTORICAL_BATTLE = "historical_battle";
    private static final String BATTLEFIELD_TYPE = "battlefield_type";
    private static final String HISTORICAL_NOTES = "historical_notes";
    private static final String FORCES = "forces";
    private static final String STARTING_FOOD = "starting_food";
    private static final String AI_DIRECTIVES = "ai_directives";

    public record ForcePlacement(Unit.SoldierCulture culture, Unit.SoldierType type, int x, int y, int size,
            int experienceLevel) {
        public Unit.SoldierCulture getCulture() { return culture; }

        public Unit.SoldierType getType() { return type; }

        public int getX() { return x; }

        public int getY() { return y; }

        public int getSize() { return size; }

        public int getExperienceLevel() { return experienceLevel; }
    }

    public record AIDirective(String doctrine, String behavior, String historicalStyle, String historicalGoal) {
        public String getDoctrine() { return doctrine; }

        public String getBehavior() { return behavior; }

        public String getHistoricalStyle() { return historicalStyle; }

        public String getHistoricalGoal() { return historicalGoal; }
    }

    public record ScenarioDefinition(String name, String historicalBattle, String battlefieldType,
            String historicalNotes, String mapPath, List<ForcePlacement> forces,
            Map<Unit.SoldierCulture, Integer> startingFood, Map<Unit.SoldierCulture, AIDirective> aiDirectives) {
        public ScenarioDefinition {
            forces = new ArrayList<>(forces);
            startingFood = new LinkedHashMap<>(startingFood);
            aiDirectives = new LinkedHashMap<>(aiDirectives);
        }

        public ScenarioDefinition(String name, String historicalBattle, String battlefieldType, String historicalNotes,
                String mapPath, List<ForcePlacement> forces) {
            this(name, historicalBattle, battlefieldType, historicalNotes, mapPath, forces, new LinkedHashMap<>(),
                    new LinkedHashMap<>());
        }

        public ScenarioDefinition(String name, String historicalBattle, String battlefieldType, String historicalNotes,
                String mapPath, List<ForcePlacement> forces, Map<Unit.SoldierCulture, AIDirective> aiDirectives) {
            this(name, historicalBattle, battlefieldType, historicalNotes, mapPath, forces, new LinkedHashMap<>(),
                    aiDirectives);
        }

        public String getName() { return name; }

        public String getHistoricalBattle() { return historicalBattle; }

        public String getBattlefieldType() { return battlefieldType; }

        public String getHistoricalNotes() { return historicalNotes; }

        public String getMapPath() { return mapPath; }

        public List<ForcePlacement> getForces() { return new ArrayList<>(forces); }

        public Map<Unit.SoldierCulture, Integer> getStartingFood() { return new LinkedHashMap<>(startingFood); }

        public int getStartingFood(Unit.SoldierCulture culture) {
            if (culture == null) {
                return 0;
            }
            Integer startingFoodAmount = startingFood.get(culture);
            return startingFoodAmount != null ? startingFoodAmount : 0;
        }

        public Map<Unit.SoldierCulture, AIDirective> getAiDirectives() { return new LinkedHashMap<>(aiDirectives); }

        public AIDirective getAiDirective(Unit.SoldierCulture culture) {
            return culture == null ? null : aiDirectives.get(culture);
        }
    }

    @SuppressWarnings("CallToPrintStackTrace")
    public String loadScenarioAsString(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                try (Scanner input = new Scanner(in, StandardCharsets.UTF_8.name())) {
                    input.useDelimiter("\\A");
                    return input.hasNext() ? input.next() : "";
                }
            }
            Path fallbackPath = resolveResourceFallbackPath(resourcePath);
            return fallbackPath != null ? Files.readString(fallbackPath, StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Path resolveResourceFallbackPath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        Path candidate = Path.of(System.getProperty("user.dir"), normalizedPath);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    public String loadScenarioTitle(String resourcePath) {
        ScenarioDefinition scenarioDefinition = loadScenarioDefinition(resourcePath);
        return scenarioDefinition != null ? scenarioDefinition.getHistoricalBattle() : null;
    }

    public String loadScenarioAiStyle(String resourcePath, String culture) {
        ScenarioDefinition scenarioDefinition = loadScenarioDefinition(resourcePath);
        if (scenarioDefinition == null || culture == null) {
            return null;
        }
        AIDirective directive = scenarioDefinition.getAiDirective(parseCulture(culture));
        return directive != null ? directive.getHistoricalStyle() : null;
    }

    public String loadScenarioMapPath(String resourcePath) {
        ScenarioDefinition scenarioDefinition = loadScenarioDefinition(resourcePath);
        return scenarioDefinition != null ? scenarioDefinition.getMapPath() : null;
    }

    public ScenarioDefinition loadScenarioDefinition(String resourcePath) {
        return parseScenarioDefinition(loadScenarioAsString(resourcePath));
    }

    public ScenarioDefinition loadScenarioDefinition(File file) {
        return parseScenarioDefinition(PersistenceManager.loadStringFromFile(file));
    }

    public boolean saveScenarioDefinition(String filePath, ScenarioDefinition scenarioDefinition) {
        if (filePath == null || scenarioDefinition == null) {
            return false;
        }
        return PersistenceManager.saveStringToFile(filePath, serializeScenarioDefinition(scenarioDefinition));
    }

    public String serializeScenarioDefinition(ScenarioDefinition scenarioDefinition) {
        StringBuilder builder = new StringBuilder();
        builder.append(toCsvLine("name", scenarioDefinition.getName())).append('\n');
        builder.append(toCsvLine(HISTORICAL_BATTLE, scenarioDefinition.getHistoricalBattle())).append('\n');
        builder.append(toCsvLine("map", scenarioDefinition.getMapPath())).append('\n');
        builder.append(toCsvLine(BATTLEFIELD_TYPE, scenarioDefinition.getBattlefieldType())).append('\n');
        builder.append(toCsvLine(HISTORICAL_NOTES, scenarioDefinition.getHistoricalNotes())).append('\n');
        builder.append("forces\n");
        builder.append("culture,type,count,x,y,size,experience\n");
        for (ForcePlacement forcePlacement : scenarioDefinition.getForces()) {
            builder.append(toCsvLine(forcePlacement.getCulture().name(), forcePlacement.getType().name(), "1",
                    Integer.toString(forcePlacement.getX()), Integer.toString(forcePlacement.getY()),
                    Integer.toString(forcePlacement.getSize()), Integer.toString(forcePlacement.getExperienceLevel())))
                    .append('\n');
        }
        if (!scenarioDefinition.getStartingFood().isEmpty()) {
            builder.append("starting_food\n");
            builder.append("culture,food\n");
            for (Map.Entry<Unit.SoldierCulture, Integer> entry : scenarioDefinition.getStartingFood().entrySet()) {
                builder.append(toCsvLine(entry.getKey().name(), Integer.toString(Math.max(0, entry.getValue()))))
                        .append('\n');
            }
        }
        builder.append("ai_directives\n");
        builder.append("culture,doctrine,behavior,historical_style,historical_goal\n");
        for (Map.Entry<Unit.SoldierCulture, AIDirective> entry : scenarioDefinition.getAiDirectives().entrySet()) {
            AIDirective directive = entry.getValue();
            builder.append(toCsvLine(entry.getKey().name(), directive.getDoctrine(), directive.getBehavior(),
                    directive.getHistoricalStyle(), directive.getHistoricalGoal())).append('\n');
        }
        return builder.toString();
    }

    private ScenarioDefinition parseScenarioDefinition(String scenarioText) {
        if (scenarioText == null || scenarioText.isBlank()) {
            return null;
        }
        String trimmed = scenarioText.trim();
        return trimmed.startsWith("{") ? parseLegacyScenarioDefinition(trimmed) : parseCsvScenarioDefinition(trimmed);
    }

    private ScenarioDefinition parseCsvScenarioDefinition(String csvText) {
        List<String> lines = csvText.lines().toList();
        String name = readMetadataValue(lines, 0, "name", "Custom Scenario");
        String historicalBattle = readMetadataValue(lines, 1, HISTORICAL_BATTLE, name);
        String mapPath = readMetadataValue(lines, 2, "map", "");
        String battlefieldType = readMetadataValue(lines, 3, BATTLEFIELD_TYPE, "custom");
        String historicalNotes = readMetadataValue(lines, 4, HISTORICAL_NOTES, "");
        int forcesSectionIndex = findSectionLine(lines, FORCES, 5);
        int startingFoodSectionIndex = findSectionLine(lines, STARTING_FOOD, Math.max(5, forcesSectionIndex + 1));
        int aiSectionIndex = findSectionLine(lines, AI_DIRECTIVES,
                Math.max(5, Math.max(forcesSectionIndex, startingFoodSectionIndex) + 1));
        List<ForcePlacement> forces = parseCsvForces(lines, forcesSectionIndex);
        Map<Unit.SoldierCulture, Integer> startingFood = parseCsvStartingFood(lines, startingFoodSectionIndex);
        Map<Unit.SoldierCulture, AIDirective> aiDirectives = parseCsvAiDirectives(lines, aiSectionIndex);
        return new ScenarioDefinition(name, historicalBattle, battlefieldType, historicalNotes, mapPath, forces,
                startingFood, aiDirectives);
    }

    private static final Set<String> SECTION_NAMES = Set.of(FORCES, STARTING_FOOD, AI_DIRECTIVES);

    private String readMetadataValue(List<String> lines, int lineIndex, String expectedKey, String fallback) {
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return fallback;
        }
        List<String> columns = parseCsvLine(lines.get(lineIndex));
        if (columns.isEmpty()) {
            return fallback;
        }
        if (columns.size() == 1) {
            return defaultString(unescapeCsv(columns.getFirst()), fallback);
        }
        if (!expectedKey.equalsIgnoreCase(columns.getFirst().trim())) {
            return fallback;
        }
        return defaultString(unescapeCsv(columns.get(1)), fallback);
    }

    private int findSectionLine(List<String> lines, String sectionName, int startIndex) {
        for (int index = Math.max(0, startIndex); index < lines.size(); index++) {
            if (sectionName.equalsIgnoreCase(lines.get(index).trim())) {
                return index;
            }
        }
        return -1;
    }

    private List<ForcePlacement> parseCsvForces(List<String> lines, int forcesSectionIndex) {
        List<ForcePlacement> forces = new ArrayList<>();
        if (forcesSectionIndex < 0) {
            return forces;
        }
        int startIndex = Math.min(lines.size(), forcesSectionIndex + 2);
        int endIndex = findNextSectionLine(lines, startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            if (columns.size() < 7) {
                continue;
            }
            Unit.SoldierCulture soldierCulture = parseCulture(columns.get(0));
            Unit.SoldierType soldierType = parseType(columns.get(1));
            int xCoord = parseInt(columns.get(3), GamePanelDefaults.DEFAULT_X);
            int yCoord = parseInt(columns.get(4), GamePanelDefaults.DEFAULT_Y);
            int unitSize = parseInt(columns.get(5), 1000);
            int experienceLevel = parseInt(columns.get(6), 1);
            forces.add(new ForcePlacement(soldierCulture, soldierType, xCoord, yCoord, unitSize, experienceLevel));
        }
        return forces;
    }

    private Map<Unit.SoldierCulture, Integer> parseCsvStartingFood(List<String> lines, int startingFoodSectionIndex) {
        Map<Unit.SoldierCulture, Integer> startingFood = new LinkedHashMap<>();
        if (startingFoodSectionIndex < 0) {
            return startingFood;
        }
        int startIndex = Math.min(lines.size(), startingFoodSectionIndex + 2);
        int endIndex = findNextSectionLine(lines, startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            if (columns.size() < 2) {
                continue;
            }
            int startingFoodAmount = parseInt(columns.get(1), -1);
            if (startingFoodAmount >= 0) {
                startingFood.put(parseCulture(columns.getFirst()), startingFoodAmount);
            }
        }
        return startingFood;
    }

    private Map<Unit.SoldierCulture, AIDirective> parseCsvAiDirectives(List<String> lines, int aiSectionIndex) {
        Map<Unit.SoldierCulture, AIDirective> directives = new LinkedHashMap<>();
        if (aiSectionIndex < 0) {
            return directives;
        }
        int startIndex = Math.min(lines.size(), aiSectionIndex + 2);
        int endIndex = findNextSectionLine(lines, startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            if (columns.size() < 5) {
                continue;
            }
            Unit.SoldierCulture soldierCulture = parseCulture(columns.get(0));
            directives.put(soldierCulture, new AIDirective(unescapeCsv(columns.get(1)), unescapeCsv(columns.get(2)),
                    unescapeCsv(columns.get(3)), unescapeCsv(columns.get(4))));
        }
        return directives;
    }

    private int findNextSectionLine(List<String> lines, int startIndex) {
        for (int index = Math.max(0, startIndex); index < lines.size(); index++) {
            if (SECTION_NAMES.contains(lines.get(index).trim().toLowerCase())) {
                return index;
            }
        }
        return lines.size();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }
        StringBuilder currentValue = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    currentValue.append('"');
                    index += 2;
                    continue;
                }
                quoted = !quoted;
                index++;
                continue;
            }
            if (current == ',' && !quoted) {
                values.add(currentValue.toString());
                currentValue.setLength(0);
                index++;
                continue;
            }
            currentValue.append(current);
            index++;
        }
        values.add(currentValue.toString());
        return values;
    }

    private String toCsvLine(String... values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(values[index]));
        }
        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\r", " ").replace("\n", "\\n");
        boolean requiresQuotes = sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\\n");
        String escaped = sanitized.replace("\"", "\"\"");
        return requiresQuotes ? '"' + escaped + '"' : escaped;
    }

    private String unescapeCsv(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\n", "\n");
    }

    private ScenarioDefinition parseLegacyScenarioDefinition(String jsonLikeText) {
        String name = defaultString(extractQuotedValue(jsonLikeText, "name"), "Custom Scenario");
        String historicalBattle = defaultString(extractQuotedValue(jsonLikeText, HISTORICAL_BATTLE), name);
        String battlefieldType = defaultString(extractQuotedValue(jsonLikeText, BATTLEFIELD_TYPE), "custom");
        String historicalNotes = defaultString(extractQuotedValue(jsonLikeText, HISTORICAL_NOTES), "");
        String mapPath = extractQuotedValue(jsonLikeText, "map");
        List<ForcePlacement> forces = parseForces(jsonLikeText);
        Map<Unit.SoldierCulture, Integer> startingFood = parseStartingFood(jsonLikeText);
        Map<Unit.SoldierCulture, AIDirective> aiDirectives = parseAiDirectives(jsonLikeText);
        return new ScenarioDefinition(name, historicalBattle, battlefieldType, historicalNotes, mapPath, forces,
                startingFood, aiDirectives);
    }

    private Map<Unit.SoldierCulture, Integer> parseStartingFood(String jsonLikeText) {
        Map<Unit.SoldierCulture, Integer> startingFood = new LinkedHashMap<>();
        String startingFoodSection = extractObjectSection(jsonLikeText, STARTING_FOOD);
        if (startingFoodSection == null || startingFoodSection.isBlank()) {
            return startingFood;
        }
        for (Unit.SoldierCulture culture : Unit.SoldierCulture.values()) {
            int amount = extractIntValue(startingFoodSection, culture.name(), -1);
            if (amount >= 0) {
                startingFood.put(culture, amount);
            }
        }
        return startingFood;
    }

    private Map<Unit.SoldierCulture, AIDirective> parseAiDirectives(String jsonLikeText) {
        Map<Unit.SoldierCulture, AIDirective> directives = new LinkedHashMap<>();
        String directivesSection = extractObjectSection(jsonLikeText, AI_DIRECTIVES);
        if (directivesSection == null || directivesSection.isBlank()) {
            return directives;
        }
        for (Unit.SoldierCulture culture : Unit.SoldierCulture.values()) {
            String directiveText = extractObjectSection(directivesSection, culture.name());
            if (directiveText == null || directiveText.isBlank()) {
                continue;
            }
            directives.put(culture,
                    new AIDirective(defaultString(extractQuotedValue(directiveText, "doctrine"), ""),
                            defaultString(extractQuotedValue(directiveText, "behavior"), ""),
                            defaultString(extractQuotedValue(directiveText, "historical_style"), ""),
                            defaultString(extractQuotedValue(directiveText, "historical_goal"), "")));
        }
        return directives;
    }

    private List<ForcePlacement> parseForces(String jsonLikeText) {
        List<ForcePlacement> forces = new ArrayList<>();
        String arraySection = extractArraySection(jsonLikeText, FORCES);
        if (arraySection == null || arraySection.isBlank()) {
            return forces;
        }
        int searchIndex = 0;
        while (searchIndex < arraySection.length()) {
            int openBrace = arraySection.indexOf('{', searchIndex);
            if (openBrace < 0) {
                break;
            }
            int closeBrace = arraySection.indexOf('}', openBrace);
            if (closeBrace < 0) {
                break;
            }
            String forceText = arraySection.substring(openBrace, closeBrace + 1);
            Unit.SoldierCulture soldierCulture = parseCulture(extractQuotedValue(forceText, "culture"));
            Unit.SoldierType soldierType = parseType(extractQuotedValue(forceText, "type"));
            int xCoord = extractIntValue(forceText, "x", GamePanelDefaults.DEFAULT_X);
            int yCoord = extractIntValue(forceText, "y", GamePanelDefaults.DEFAULT_Y);
            int unitSize = extractIntValue(forceText, "size", 1000);
            int experienceLevel = extractIntValue(forceText, "experience",
                    extractIntValue(forceText, "experienceLevel", 1));
            forces.add(new ForcePlacement(soldierCulture, soldierType, xCoord, yCoord, unitSize, experienceLevel));
            searchIndex = closeBrace + 1;
        }
        return forces;
    }

    private String extractArraySection(String jsonLikeText, String key) {
        if (jsonLikeText == null || key == null) {
            return null;
        }
        String marker = '"' + key + '"';
        int keyIndex = jsonLikeText.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int arrayStart = jsonLikeText.indexOf('[', keyIndex + marker.length());
        if (arrayStart < 0) {
            return null;
        }
        int depth = 0;
        for (int index = arrayStart; index < jsonLikeText.length(); index++) {
            char current = jsonLikeText.charAt(index);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return jsonLikeText.substring(arrayStart + 1, index);
                }
            }
        }
        return null;
    }

    private String extractObjectSection(String jsonLikeText, String key) {
        if (jsonLikeText == null || key == null) {
            return null;
        }
        String marker = '"' + key + '"';
        int keyIndex = jsonLikeText.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int objectStart = jsonLikeText.indexOf('{', keyIndex + marker.length());
        if (objectStart < 0) {
            return null;
        }
        int depth = 0;
        for (int index = objectStart; index < jsonLikeText.length(); index++) {
            char current = jsonLikeText.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return jsonLikeText.substring(objectStart + 1, index);
                }
            }
        }
        return null;
    }

    private int extractIntValue(String jsonLikeText, String key, int fallback) {
        if (jsonLikeText == null || key == null) {
            return fallback;
        }
        String marker = '"' + key + '"';
        int keyIndex = jsonLikeText.indexOf(marker);
        if (keyIndex < 0) {
            return fallback;
        }
        int colonIndex = jsonLikeText.indexOf(':', keyIndex + marker.length());
        if (colonIndex < 0) {
            return fallback;
        }
        int valueStart = colonIndex + 1;
        while (valueStart < jsonLikeText.length() && Character.isWhitespace(jsonLikeText.charAt(valueStart))) {
            valueStart++;
        }
        int valueEnd = valueStart;
        while (valueEnd < jsonLikeText.length()
                && (Character.isDigit(jsonLikeText.charAt(valueEnd)) || jsonLikeText.charAt(valueEnd) == '-')) {
            valueEnd++;
        }
        if (valueStart == valueEnd) {
            return fallback;
        }
        try {
            return Integer.parseInt(jsonLikeText.substring(valueStart, valueEnd));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private Unit.SoldierCulture parseCulture(String value) {
        if (value == null) {
            return Unit.SoldierCulture.ROMAN;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return Unit.SoldierCulture.ROMAN;
        }
        switch (normalized) {
        case "PTOL", "PTOLEMY", "PTOLEMAIC", "PTOLEMIES", "PTOLEMYAN" -> {
            return Unit.SoldierCulture.EGYPTIAN;
        }
        case "OPTIMATE", "OPTIMATES" -> {
            return Unit.SoldierCulture.POMPEIAN;
        }
        default -> {
            try {
                return Unit.SoldierCulture.valueOf(normalized);
            } catch (IllegalArgumentException _) {
                return Unit.SoldierCulture.ROMAN;
            }
        }
        }
    }

    private Unit.SoldierType parseType(String value) {
        if (value == null) {
            return Unit.SoldierType.ROMAN_HEAVY_INFANTRY;
        }
        try {
            return Unit.SoldierType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException _) {
            return Unit.SoldierType.ROMAN_HEAVY_INFANTRY;
        }
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private String defaultString(String value, String fallback) { return value == null ? fallback : value; }

    private static final class GamePanelDefaults {
        private static final int DEFAULT_X = 100;
        private static final int DEFAULT_Y = 100;
    }

    private String extractQuotedValue(String jsonLikeText, String key) {
        if (jsonLikeText == null || key == null) {
            return null;
        }
        String marker = '"' + key + '"';
        int keyIndex = jsonLikeText.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = jsonLikeText.indexOf(':', keyIndex + marker.length());
        int startQuote = jsonLikeText.indexOf('"', colonIndex + 1);
        int endQuote = jsonLikeText.indexOf('"', startQuote + 1);
        if (colonIndex < 0 || startQuote < 0 || endQuote < 0) {
            return null;
        }
        return jsonLikeText.substring(startQuote + 1, endQuote);
    }
}
