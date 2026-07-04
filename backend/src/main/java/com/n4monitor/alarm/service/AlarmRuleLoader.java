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
 * 报警规则加载器
 * <p>
 * 1. 从 Excel (alarm_rules_config.xlsx) 读取用户已配置的规则
 * 2. 从日志文件 (points-2.log) 分析所有点位及其数值范围
 * 3. 对 Excel 中未配置的点位，根据日志数据自动生成模拟规则
 * 4. 合并后存入 Redis 供 Kafka 消费者使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmRuleLoader {

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "timestamp=\\[([^\\]]+)]\\s*,topic=([^,]+)\\s*,key=(.*?)\\s*,value=(.*)");

    /** Excel 中每个阈值分级对应的列偏移 (Value, Severity, Message) */
    private static final String[] THRESHOLD_LEVELS = {"Normal", "High", "High-High", "Low", "Low-Low", "Offline"};

    /** 每个分级在 Excel 中占 3 列，从第 2 列开始 */
    private static final int[][] LEVEL_COLUMNS = {
            {2, 3, 4},    // Normal:    Value, Severity, Message
            {5, 6, 7},    // High:      Value, Severity, Message
            {8, 9, 10},   // High-High: Value, Severity, Message
            {11, 12, 13}, // Low:       Value, Severity, Message
            {14, 15, 16}, // Low-Low:   Value, Severity, Message
            {17, 18, 19}, // Offline:   Value, Severity, Message
    };

    private final AlarmRuleService alarmRuleService;

    // ══════════════════════════════════════════════════════════════════
    //  主入口: 加载规则 (Excel + 日志自动补全) → Redis
    // ══════════════════════════════════════════════════════════════════

    /**
     * 从 Excel + 日志加载规则并写入 Redis
     *
     * @param excelFilePath Excel 规则配置文件路径 (alarm_rules_config.xlsx)
     * @param logFilePath   日志文件路径 (points-2.log)
     */
    public void loadRules(String excelFilePath, String logFilePath) {
        log.info("══════════════════════════════════════════════════");
        log.info("开始加载报警规则");

        // 1. 读取 Excel 中用户已配置的规则
        Map<String, List<AlarmRule>> excelRules = readExcelRules(excelFilePath);
        log.info("[1/3] Excel 用户配置: {} 个点位", excelRules.size());

        // 2. 解析日志，统计各点位数据
        File logFile = resolveFile(logFilePath);
        if (logFile == null) {
            log.error("无法找到日志文件: {}", logFilePath);
            if (excelRules.isEmpty()) {
                log.error("Excel 和日志均无数据, 无法加载规则");
                return;
            }
            log.warn("仅使用 Excel 规则 (无日志数据用于补全)");
            alarmRuleService.saveAllRules(excelRules);
            log.info("规则加载完成 (仅 Excel): {} 个点位", excelRules.size());
            return;
        }

        Map<String, PointStats> pointStatsMap = analyzeLog(logFile.getAbsolutePath());
        log.info("[2/3] 日志分析: {} 个唯一点位", pointStatsMap.size());

        // 3. 合并: Excel 配置优先, 未配置的从日志自动生成
        Map<String, List<AlarmRule>> allRules = new LinkedHashMap<>();
        int userCount = 0;
        int autoCount = 0;

        for (Map.Entry<String, PointStats> entry : pointStatsMap.entrySet()) {
            String path = entry.getKey();
            PointStats stats = entry.getValue();

            if (excelRules.containsKey(path)) {
                // 用户已配置 → 直接使用
                allRules.put(path, excelRules.get(path));
                userCount++;
            } else {
                // 未配置 → 自动生成
                List<AlarmRule> rules = generateRules(path, stats);
                if (!rules.isEmpty()) {
                    allRules.put(path, rules);
                    autoCount++;
                }
            }
        }

        // Excel 中有但日志中没有的点位也加入
        for (Map.Entry<String, List<AlarmRule>> entry : excelRules.entrySet()) {
            if (!allRules.containsKey(entry.getKey())) {
                allRules.put(entry.getKey(), entry.getValue());
                userCount++;
            }
        }

        // 4. 存入 Redis
        alarmRuleService.saveAllRules(allRules);
        log.info("[3/3] 规则加载完成: 用户配置={} 自动生成={} 总计={}",
                userCount, autoCount, allRules.size());
        log.info("══════════════════════════════════════════════════");
    }

    // ══════════════════════════════════════════════════════════════════
    //  Excel 读取
    // ══════════════════════════════════════════════════════════════════

    /**
     * 读取 Excel 文件中用户已配置的报警规则
     *
     * Excel 结构:
     *   Row 1: 合并的分级表头 (Normal | High | High-High | Low | Low-Low | Offline)
     *   Row 2: 子表头 (Value | Severity | Message) × 6
     *   Row 3+: 数据行, Column A = path
     */
    private Map<String, List<AlarmRule>> readExcelRules(String excelFilePath) {
        Map<String, List<AlarmRule>> rules = new LinkedHashMap<>();

        File excelFile = resolveFile(excelFilePath);
        if (excelFile == null) {
            log.warn("未找到 Excel 配置文件: {}, 跳过 Excel 读取", excelFilePath);
            return rules;
        }

        log.info("读取 Excel 规则配置: {}", excelFile.getAbsolutePath());

        try (Workbook wb = new XSSFWorkbook(excelFile)) {
            Sheet sheet = wb.getSheet("Alarm Rules");
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }

            // 从第 3 行开始读数据 (Row 1-2 是表头)
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String path = getCellString(row.getCell(0));
                if (path == null || path.isBlank()) continue;

                List<AlarmRule> pointRules = new ArrayList<>();
                boolean hasAnyConfig = false;

                for (int i = 0; i < THRESHOLD_LEVELS.length; i++) {
                    String level = THRESHOLD_LEVELS[i];
                    int valCol = LEVEL_COLUMNS[i][0];
                    int sevCol = LEVEL_COLUMNS[i][1];
                    int msgCol = LEVEL_COLUMNS[i][2];

                    String value = getCellString(row.getCell(valCol - 1)); // POI 0-based
                    String severity = getCellString(row.getCell(sevCol - 1));
                    String message = getCellString(row.getCell(msgCol - 1));

                    // 判断该分级是否已配置
                    boolean configured;
                    if (level.equals("Normal")) {
                        configured = (severity != null && !severity.isBlank())
                                || (message != null && !message.isBlank());
                    } else if (level.equals("Offline")) {
                        // Offline 的 value 预填了 "Null", 看 severity/message
                        configured = (severity != null && !severity.isBlank())
                                || (message != null && !message.isBlank());
                    } else {
                        configured = value != null && !value.isBlank();
                    }

                    if (configured) {
                        hasAnyConfig = true;
                        pointRules.add(AlarmRule.builder()
                                .level(level)
                                .value(value)
                                .severity(severity)
                                .message(message)
                                .build());
                    }
                }

                if (hasAnyConfig) {
                    rules.put(path, pointRules);
                }
            }

        } catch (Exception e) {
            log.error("读取 Excel 文件失败: {}", excelFile.getAbsolutePath(), e);
        }

        return rules;
    }

    /** 安全读取单元格字符串值 */
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
    //  日志分析
    // ══════════════════════════════════════════════════════════════════

    private Map<String, PointStats> analyzeLog(String filePath) {
        Map<String, PointStats> stats = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = LOG_LINE_PATTERN.matcher(line.trim());
                if (!m.matches()) continue;

                String key = m.group(3).trim();
                String valueJson = m.group(4).trim();

                String name = extractJsonField(valueJson, "name");
                String value = extractJsonField(valueJson, "value");

                stats.computeIfAbsent(key, k -> new PointStats(name))
                     .addValue(value);
            }
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", filePath, e);
        }

        return stats;
    }

    /** 简单提取 JSON 字段值 */
    private String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  路径解析
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

            File f4 = new File(parentDir, "材料" + File.separator + new File(cleanPath).getName());
            if (f4.exists()) return f4;
        }

        return null;
    }

    // ══════════════════════════════════════════════════════════════════
    //  规则自动生成 (用于 Excel 中未配置的点位)
    //  每个点位都生成完整的 6 种分级规则, value/severity/message 全部填充
    // ══════════════════════════════════════════════════════════════════

    private static final Random RANDOM = new Random(42); // 固定种子保证可重现

    private List<AlarmRule> generateRules(String path, PointStats stats) {
        String shortName = path.substring(path.lastIndexOf('/') + 1);
        PointType type = stats.classify();
        List<AlarmRule> rules = new ArrayList<>();

        switch (type) {
            case STRING -> generateStringRules(rules, shortName);
            case BOOL -> generateBoolRules(rules, shortName, stats);
            case NUMERIC -> generateNumericRules(rules, shortName, stats);
        }

        // Offline 规则（所有类型通用，始终生成）
        rules.add(AlarmRule.builder()
                .level("Offline").value("Null").severity("Critical")
                .message(shortName + " is offline - no data received")
                .build());

        return rules;
    }

    // ── 字符串型: 6 级全填充 ──────────────────────────────────────────

    private void generateStringRules(List<AlarmRule> rules, String name) {
        rules.add(AlarmRule.builder()
                .level("Normal").value(null).severity("Normal")
                .message(name + " operating normally").build());
        rules.add(AlarmRule.builder()
                .level("High").value("UNEXPECTED").severity("Major")
                .message(name + " value unexpectedly changed").build());
        rules.add(AlarmRule.builder()
                .level("High-High").value("CRITICAL_CHANGE").severity("Critical")
                .message(name + " critical value change detected").build());
        rules.add(AlarmRule.builder()
                .level("Low").value("DEGRADED").severity("Minor")
                .message(name + " value degraded").build());
        rules.add(AlarmRule.builder()
                .level("Low-Low").value("INVALID").severity("Warning")
                .message(name + " value invalid or missing").build());
    }

    // ── 布尔型: 6 级全填充, 随机选择报警状态 ──────────────────────────

    private void generateBoolRules(List<AlarmRule> rules, String name, PointStats stats) {
        Set<String> values = stats.uniqueValues;
        String nameLower = name.toLowerCase();
        String normalVal = values.contains("false") ? "false" : "true";
        String alarmVal = "true".equals(normalVal) ? "false" : "true";

        // 通信/电源状态: false = 报警
        if (nameLower.contains("communication") || nameLower.contains("comm")) {
            normalVal = "true";
            alarmVal = "false";
        } else if (nameLower.contains("power_supply")) {
            normalVal = values.contains("true") ? "true" : "false";
            alarmVal = "true".equals(normalVal) ? "false" : "true";
        } else {
            // 通用开关量: 随机决定哪个状态是报警
            if (RANDOM.nextBoolean()) {
                normalVal = "false";
                alarmVal = "true";
            } else {
                normalVal = "false";
                alarmVal = "true";
            }
        }

        rules.add(AlarmRule.builder()
                .level("Normal").value(normalVal).severity("Normal")
                .message(name + " normal state: " + normalVal).build());
        rules.add(AlarmRule.builder()
                .level("High").value(alarmVal).severity("Major")
                .message(name + " state changed to " + alarmVal).build());
        rules.add(AlarmRule.builder()
                .level("High-High").value(alarmVal).severity("Critical")
                .message(name + " critical state: " + alarmVal).build());
        rules.add(AlarmRule.builder()
                .level("Low").value(alarmVal).severity("Minor")
                .message(name + " minor state deviation: " + alarmVal).build());
        rules.add(AlarmRule.builder()
                .level("Low-Low").value(alarmVal).severity("Warning")
                .message(name + " warning state: " + alarmVal).build());
    }

    // ── 数值型: 6 级全填充, 阈值贴近实际数据范围 ──────────────────────

    private void generateNumericRules(List<AlarmRule> rules, String name, PointStats stats) {
        String nameLower = name.toLowerCase();
        double avg = stats.getNumericAvg();
        double min = stats.numericMin;
        double max = stats.numericMax;

        // 获取阈值: 所有 4 个阈值都不为 null
        Thresholds t = getNumericThresholds(nameLower, avg, min, max);

        // Normal
        rules.add(AlarmRule.builder()
                .level("Normal").value(null).severity("Normal")
                .message(name + " within normal range").build());

        // High-High (Critical)
        rules.add(AlarmRule.builder()
                .level("High-High").value(String.valueOf(t.highHigh)).severity("Critical")
                .message(name + " critically high: value >= " + t.highHigh).build());

        // High (Major)
        rules.add(AlarmRule.builder()
                .level("High").value(String.valueOf(t.high)).severity("Major")
                .message(name + " high: value >= " + t.high).build());

        // Low (Minor)
        rules.add(AlarmRule.builder()
                .level("Low").value(String.valueOf(t.low)).severity("Minor")
                .message(name + " low: value <= " + t.low).build());

        // Low-Low (Warning)
        rules.add(AlarmRule.builder()
                .level("Low-Low").value(String.valueOf(t.lowLow)).severity("Warning")
                .message(name + " critically low: value <= " + t.lowLow).build());
    }

    /**
     * 获取数值型阈值: 阈值贴近实际数据范围以增加报警概率
     * 所有 4 个阈值都保证非 null
     */
    private Thresholds getNumericThresholds(String name, double avg, double min, double max) {
        // 基于领域知识的阈值, 但调紧以增加报警概率
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
            // 静态值: 用宽阈值
            hh = Double.MAX_VALUE; h = Double.MAX_VALUE; l = -Double.MAX_VALUE; ll = -Double.MAX_VALUE;
        }

        // 默认: 基于数据范围, 阈值贴近实际值
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
                // 全 0 数据: 设很小的阈值让 0 也触发报警
                hh = 0.01; h = 0.001; l = -0.001; ll = -0.01;
            }
        }

        return new Thresholds(hh, h, l, ll);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ══════════════════════════════════════════════════════════════════
    //  内部数据类
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
