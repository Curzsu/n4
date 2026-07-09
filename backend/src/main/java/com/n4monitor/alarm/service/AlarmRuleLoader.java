package com.n4monitor.alarm.service;

import com.n4monitor.alarm.model.AlarmRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Alarm rule loader.
 * <p>
 * 1. Read user-configured rules from Excel (alarm_rules_config.xlsx).
 * 2. Only points present in the Excel sheet are processed.
 * 3. Validate mutual exclusion: a point cannot have both Boolean and numeric rules.
 * 4. For points in Excel but without any rule configuration, auto-generate mock rules from the log file.
 * 5. Store merged rules in Redis for Kafka consumers to use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleLoader {

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "timestamp=\\[([^\\]]+)]\\s*,topic=([^,]+)\\s*,key=(.*?)\\s*,value=(.*)");

    /** Threshold levels in the Excel sheet. */
    private static final String[] THRESHOLD_LEVELS = {"Boolean", "High", "High-High", "Low", "Low-Low"};

    /** Column layout for each level (1-based column index, sub-column names). */
    private static final Map<String, LevelColumns> LEVEL_COLUMNS_MAP = Map.of(
            "Boolean", new LevelColumns(2, List.of("Value", "Severity", "Message")),
            "High", new LevelColumns(5, List.of("Value", "Severity", "Message", "Hysteresis")),
            "High-High", new LevelColumns(9, List.of("Value", "Severity", "Message", "Hysteresis")),
            "Low", new LevelColumns(13, List.of("Value", "Severity", "Message", "Hysteresis")),
            "Low-Low", new LevelColumns(17, List.of("Value", "Severity", "Message", "Hysteresis"))
    );

    private final AlarmRuleService alarmRuleService;

    record LevelColumns(int startCol, List<String> subColumns) {}

    // ══════════════════════════════════════════════════════════════════
    //  Main entry: load rules (Excel + log auto-completion) → Redis
    // ══════════════════════════════════════════════════════════════════

    /**
     * Load rules from Excel + log and write to Redis.
     * <p>
     * Only points listed in the Excel sheet are processed. If a point has no
     * user-configured rules, mock rules are generated from the log data.
     *
     * @param excelFilePath Excel rule config file path (alarm_rules_config.xlsx)
     * @param logFilePath   Log file path (points-2.log)
     */
    public void loadRules(String excelFilePath, String logFilePath) {
        log.info("══════════════════════════════════════════════════");
        log.info("Starting to load alarm rules");

        // 1. Read all points from Excel (including empty ones)
        Map<String, List<AlarmRule>> excelRules = readExcelRules(excelFilePath);
        log.info("[1/3] Excel points loaded: {}", excelRules.size());

        if (excelRules.isEmpty()) {
            log.warn("No points found in Excel config file, skipping rule loading");
            return;
        }

        // 2. Analyze log only for points present in Excel
        File logFile = resolveFile(logFilePath);
        Map<String, PointStats> pointStatsMap = new LinkedHashMap<>();
        if (logFile != null) {
            pointStatsMap = analyzeLogForPaths(logFile.getAbsolutePath(), excelRules.keySet());
            log.info("[2/3] Log analysis: {} points matched Excel config", pointStatsMap.size());
        } else {
            log.warn("Cannot find log file: {}", logFilePath);
        }

        // 3. Merge: Excel config takes priority; auto-generate for empty points
        Map<String, List<AlarmRule>> allRules = new LinkedHashMap<>();
        int userCount = 0;
        int autoCount = 0;
        int skippedCount = 0;

        for (String path : excelRules.keySet()) {
            List<AlarmRule> userRules = excelRules.get(path);

            if (!userRules.isEmpty()) {
                allRules.put(path, userRules);
                userCount++;
            } else {
                PointStats stats = pointStatsMap.get(path);
                if (stats != null) {
                    List<AlarmRule> generated = generateRules(path, stats);
                    if (!generated.isEmpty()) {
                        allRules.put(path, generated);
                        autoCount++;
                    } else {
                        skippedCount++;
                        log.warn("Auto-generation produced no rules for point: {}", path);
                    }
                } else {
                    skippedCount++;
                    log.warn("No log data available for point: {}", path);
                }
            }
        }

        // 4. Store in Redis
        alarmRuleService.saveAllRules(allRules);
        log.info("[3/3] Rule loading complete: user config={} auto generated={} skipped={} total={}",
                userCount, autoCount, skippedCount, allRules.size());
        log.info("══════════════════════════════════════════════════");
    }

    // ══════════════════════════════════════════════════════════════════
    //  Excel reading
    // ══════════════════════════════════════════════════════════════════

    /**
     * Read user-configured alarm rules from Excel file.
     * <p>
     * Excel structure:
     *   Row 1: Merged level header (Boolean | High | High-High | Low | Low-Low)
     *   Row 2: Sub-header (Value | Severity | Message | Hysteresis) per level
     *   Row 3+: Data rows, Column A = path
     *
     * @return Map of path to rule list. Paths with no configured rules map to an empty list.
     */
    private Map<String, List<AlarmRule>> readExcelRules(String excelFilePath) {
        Map<String, List<AlarmRule>> rules = new LinkedHashMap<>();

        File excelFile = resolveFile(excelFilePath);
        if (excelFile == null) {
            log.warn("Excel config file not found: {}, skipping Excel read", excelFilePath);
            return rules;
        }

        log.info("Reading Excel rule config: {}", excelFile.getAbsolutePath());

        try (Workbook wb = new XSSFWorkbook(excelFile)) {
            Sheet sheet = wb.getSheet("Alarm Rules");
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }

            // Read data from row 3 (rows 1-2 are headers)
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String path = getCellString(row.getCell(0));
                if (path == null || path.isBlank()) continue;

                List<AlarmRule> pointRules = new ArrayList<>();
                boolean booleanConfigured = false;
                boolean numericConfigured = false;

                for (String level : THRESHOLD_LEVELS) {
                    LevelColumns lc = LEVEL_COLUMNS_MAP.get(level);

                    String value = getCellString(row.getCell(lc.startCol() - 1));
                    String severity = getCellString(row.getCell(lc.startCol())); // startCol is 1-based
                    String message = getCellString(row.getCell(lc.startCol() + 1));
                    String hysteresis = null;
                    if (lc.subColumns().size() > 3) {
                        hysteresis = getCellString(row.getCell(lc.startCol() + 2));
                    }

                    boolean configured = value != null && !value.isBlank();
                    if (!configured) continue;

                    if ("Boolean".equals(level)) {
                        booleanConfigured = true;
                    } else {
                        numericConfigured = true;
                    }

                    pointRules.add(AlarmRule.builder()
                            .level(level)
                            .value(value)
                            .severity(severity)
                            .message(message)
                            .hysteresis(hysteresis)
                            .build());
                }

                // Mutual exclusion validation
                if (booleanConfigured && numericConfigured) {
                    throw new IllegalStateException(
                            "Point '" + path + "' has both Boolean and numeric alarm rules configured. " +
                                    "Only one value type is allowed per point.");
                }

                // Determine point type and assign it to each rule
                String pointType = booleanConfigured ? "BOOL" : (numericConfigured ? "NUMERIC" : null);
                if (pointType != null) {
                    for (AlarmRule rule : pointRules) {
                        rule.setPointType(pointType);
                    }
                }

                rules.put(path, pointRules);
            }

        } catch (Exception e) {
            log.error("Failed to read Excel file: {}", excelFile.getAbsolutePath(), e);
            throw new RuntimeException("Failed to read alarm rule Excel file", e);
        }

        return rules;
    }

    /** Safely read cell string value. */
    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    // ══════════════════════════════════════════════════════════════════
    //  Log analysis
    // ══════════════════════════════════════════════════════════════════

    /**
     * Analyze the log file and collect statistics only for the given set of paths.
     */
    private Map<String, PointStats> analyzeLogForPaths(String filePath, Set<String> targetPaths) {
        Map<String, PointStats> stats = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = LOG_LINE_PATTERN.matcher(line.trim());
                if (!m.matches()) continue;

                String key = m.group(3).trim();
                if (!targetPaths.contains(key)) continue;

                String valueJson = m.group(4).trim();
                String name = extractJsonField(valueJson, "name");
                String value = extractJsonField(valueJson, "value");

                stats.computeIfAbsent(key, k -> new PointStats(name))
                     .addValue(value);
            }
        } catch (IOException e) {
            log.error("Failed to read log file: {}", filePath, e);
        }

        return stats;
    }

    /** Simple extraction of JSON field value. */
    private String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Path resolution
    // ══════════════════════════════════════════════════════════════════

    private File resolveFile(String path) {
        File f = new File(path);
        if (f.exists()) return f;

        String userDir = System.getProperty("user.dir");
        File f2 = new File(userDir, path);
        if (f2.exists()) return f2;

        File parentDir = new File(userDir).getParentFile();
        if (parentDir != null) {
            String cleanPath = path.replace("../", "").replace("..\\", "");
            File f3 = new File(parentDir, cleanPath);
            if (f3.exists()) return f3;

            File f4 = new File(parentDir, "materials" + File.separator + new File(cleanPath).getName());
            if (f4.exists()) return f4;
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Auto rule generation (for points in Excel but not configured)
    // ══════════════════════════════════════════════════════════════════

    private static final Random RANDOM = new Random(42);

    private List<AlarmRule> generateRules(String path, PointStats stats) {
        String shortName = path.substring(path.lastIndexOf('/') + 1);
        PointType type = stats.classify();
        List<AlarmRule> rules = new ArrayList<>();

        switch (type) {
            case BOOL -> generateBoolRules(rules, shortName, stats);
            case NUMERIC -> generateNumericRules(rules, shortName, stats);
            case STRING -> log.warn("Point '{}' is classified as STRING; skipping auto-generation.", path);
        }

        return rules;
    }

    private void generateBoolRules(List<AlarmRule> rules, String name, PointStats stats) {
        // Boolean points always trigger an alarm when the value is false.
        rules.add(AlarmRule.builder()
                .level("Boolean")
                .value("false")
                .severity("Critical")
                .message(name + " state changed to false")
                .hysteresis(null)
                .pointType("BOOL")
                .build());
    }

    private void generateNumericRules(List<AlarmRule> rules, String name, PointStats stats) {
        String nameLower = name.toLowerCase();
        double avg = stats.getNumericAvg();
        double min = stats.numericMin;
        double max = stats.numericMax;

        Thresholds t = getNumericThresholds(nameLower, avg, min, max);

        rules.add(AlarmRule.builder()
                .level("High-High")
                .value(String.valueOf(t.highHigh()))
                .severity("Critical")
                .message(name + " critically high: value >= " + t.highHigh())
                .hysteresis("0.00")
                .pointType("NUMERIC")
                .build());

        rules.add(AlarmRule.builder()
                .level("High")
                .value(String.valueOf(t.high()))
                .severity("Major")
                .message(name + " high: value >= " + t.high())
                .hysteresis("0.00")
                .pointType("NUMERIC")
                .build());

        rules.add(AlarmRule.builder()
                .level("Low")
                .value(String.valueOf(t.low()))
                .severity("Minor")
                .message(name + " low: value <= " + t.low())
                .hysteresis("0.00")
                .pointType("NUMERIC")
                .build());

        rules.add(AlarmRule.builder()
                .level("Low-Low")
                .value(String.valueOf(t.lowLow()))
                .severity("Warning")
                .message(name + " critically low: value <= " + t.lowLow())
                .hysteresis("0.00")
                .pointType("NUMERIC")
                .build());
    }

    /**
     * Get numeric thresholds close to the actual data range to increase alarm probability.
     * All four thresholds are guaranteed non-null.
     */
    private Thresholds getNumericThresholds(String name, double avg, double min, double max) {
        Double hh = null, h = null, l = null, ll = null;

        if (name.contains("voltage")) {
            if (name.contains("_a_n") || name.contains("_b_n") || name.contains("_c_n")
                    || name.contains("voltage_a") && !name.contains("voltage_ab")) {
                hh = 242.0; h = 230.0; l = 210.0; ll = 198.0;
            } else {
                hh = 418.0; h = 399.0; l = 361.0; ll = 342.0;
            }
        } else if (name.contains("current")) {
            if (name.contains("neutral")) { hh = 5.0; h = 3.0; l = 0.5; ll = 0.1; }
            else { hh = 80.0; h = 60.0; l = 10.0; ll = 5.0; }
        } else if (name.contains("power") && !name.contains("factor")) {
            hh = 30000.0; h = 20000.0; l = 100.0; ll = 10.0;
        } else if (name.contains("power_factor") || name.contains("pf")) {
            hh = 1.0; h = 0.95; l = 0.8; ll = 0.6;
        } else if (name.contains("frequency")) {
            hh = 51.0; h = 50.5; l = 49.5; ll = 49.0;
        } else if (name.contains("cpu")) {
            hh = 70.0; h = 50.0; l = 1.0; ll = 0.5;
        } else if (name.contains("memory_usage")) {
            hh = 80.0; h = 70.0; l = 5.0; ll = 2.0;
        } else if (name.contains("disk_usage") || name.contains("hard_disk_usage")) {
            hh = 80.0; h = 70.0; l = 5.0; ll = 2.0;
        } else if (name.contains("disk_reading") || name.contains("disk_write")) {
            hh = 50.0; h = 20.0; l = 0.5; ll = 0.1;
        } else if (name.contains("speed") || name.contains("flow") || name.contains("uplink") || name.contains("downlink")) {
            hh = 10.0; h = 5.0; l = 0.5; ll = 0.1;
        } else if (name.contains("available_physical_memory")) {
            hh = 2000.0; h = 1500.0; l = 500.0; ll = 200.0;
        } else if (name.contains("remaining_disk")) {
            hh = 10.0; h = 8.0; l = 3.0; ll = 1.5;
        } else if (name.contains("total_size") || name.contains("total_physical") || "ct".equals(name) || "pt".equals(name)) {
            hh = Double.MAX_VALUE; h = Double.MAX_VALUE; l = -Double.MAX_VALUE; ll = -Double.MAX_VALUE;
        }

        if (hh == null) {
            if (avg > 0) {
                hh = round2(avg * 1.3);
                h = round2(avg * 1.1);
                l = round2(avg * 0.7);
                ll = round2(avg * 0.3);
            } else if (max > 0) {
                hh = round2(max * 1.1);
                h = round2(max * 1.0);
                l = round2(min * 0.9);
                ll = round2(min * 0.5);
            } else {
                hh = 0.01; h = 0.001; l = -0.001; ll = -0.01;
            }
        }

        return new Thresholds(hh, h, l, ll);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ══════════════════════════════════════════════════════════════════
    //  Internal data classes
    // ══════════════════════════════════════════════════════════════════

    enum PointType { BOOL, NUMERIC, STRING }

    static class PointStats {
        String name;
        final List<String> values = new ArrayList<>();
        final Set<String> uniqueValues = new HashSet<>();
        double numericMin = Double.MAX_VALUE, numericMax = -Double.MAX_VALUE, numericSum = 0;
        int numericCount = 0;
        boolean isNumeric = true;
        boolean isBool = true;

        PointStats(String name) { this.name = name; }

        void addValue(String v) {
            if (v == null) return;
            values.add(v);
            uniqueValues.add(v);
            if (!"true".equals(v) && !"false".equals(v)) isBool = false;
            try {
                double d = Double.parseDouble(v);
                numericMin = Math.min(numericMin, d);
                numericMax = Math.max(numericMax, d);
                numericSum += d;
                numericCount++;
            } catch (NumberFormatException e) {
                isNumeric = false;
            }
        }

        double getNumericAvg() {
            return numericCount > 0 ? numericSum / numericCount : 0;
        }

        PointType classify() {
            if (isBool && uniqueValues.stream().allMatch(v -> "true".equals(v) || "false".equals(v)))
                return PointType.BOOL;
            if (isNumeric && numericCount > 0) return PointType.NUMERIC;
            return PointType.STRING;
        }
    }

    record Thresholds(Double highHigh, Double high, Double low, Double lowLow) {}
}
