"""
迁移脚本：根据日志数据自动生成报警规则，写入 MySQL。

直接读取 points-2.log，对每个唯一 path 的点位进行分类：
- boolean 点位：生成 1 条规则（value=false/Critical）写入 alarm_rule_boolean 表
- numeric 点位：根据名称匹配阈值表生成 High-High/High/Low/Low-Low 四等级写入 alarm_rule_numeric 表
- string 点位：不生成规则（无字符串类型的报警规则），跳过

alarm_name = {pointName}_{序号}，序号从 00 开始，同名点位（不同设备下）递增。

用法：
    pip install pymysql
    python migrate_auto_rules.py
"""

import json
import re
import sys
from pathlib import Path
from collections import defaultdict

import pymysql

# ═══════════════════════════════════════════════════════════════════════
# 配置
# ═══════════════════════════════════════════════════════════════════════

LOG_FILE = str(Path(__file__).parent.parent / "材料" / "points-2.log")

MYSQL_HOST = "localhost"
MYSQL_PORT = 3306
MYSQL_USER = "root"
MYSQL_PASSWORD = "1234"
MYSQL_DB = "n4_monitor"

# 日志行正则
LOG_LINE_PATTERN = re.compile(
    r'timestamp=\[([^\]]+)\]\s*,topic=([^,]+)\s*,key=(.*?)\s*,value=(.*)'
)

# ═══════════════════════════════════════════════════════════════════════
# 日志分析
# ═══════════════════════════════════════════════════════════════════════

class PointStats:
    def __init__(self, name: str):
        self.name = name
        self.unique_values = set()
        self.numeric_min = float('inf')
        self.numeric_max = float('-inf')
        self.numeric_sum = 0.0
        self.numeric_count = 0
        self.is_numeric = True
        self.is_bool = True

    def add_value(self, v: str):
        if v is None:
            return
        self.unique_values.add(v)
        if v not in ("true", "false"):
            self.is_bool = False
        try:
            d = float(v)
            self.numeric_min = min(self.numeric_min, d)
            self.numeric_max = max(self.numeric_max, d)
            self.numeric_sum += d
            self.numeric_count += 1
        except ValueError:
            self.is_numeric = False

    @property
    def numeric_avg(self):
        return self.numeric_sum / self.numeric_count if self.numeric_count > 0 else 0.0

    def classify(self) -> str:
        if self.is_bool and all(v in ("true", "false") for v in self.unique_values):
            return "BOOL"
        if self.is_numeric and self.numeric_count > 0:
            return "NUMERIC"
        return "STRING"


def extract_json_field(json_str: str, field: str) -> str | None:
    p = re.compile(r'"' + field + r'"\s*:\s*"([^"]*)"')
    m = p.search(json_str)
    return m.group(1) if m else None


def analyze_log(log_path: str) -> dict[str, PointStats]:
    """读取日志，对每个唯一 path 收集统计数据。"""
    stats = {}
    with open(log_path, "r", encoding="utf-8") as f:
        for line in f:
            m = LOG_LINE_PATTERN.match(line.strip())
            if not m:
                continue
            key = m.group(3).strip()
            value_json = m.group(4).strip()
            name = extract_json_field(value_json, "name") or ""
            value = extract_json_field(value_json, "value")
            stats.setdefault(key, PointStats(name)).add_value(value)
    return stats


# ═══════════════════════════════════════════════════════════════════════
# 规则生成
# ═══════════════════════════════════════════════════════════════════════

def round2(v: float) -> float:
    return round(v * 100.0) / 100.0


def get_numeric_thresholds(name: str, avg: float, mn: float, mx: float) -> tuple:
    """返回 (high_high, high, low, low_low)，完整复制 Java getNumericThresholds 逻辑。"""
    name = name.lower()
    hh = h = l = ll = None

    if "voltage" in name:
        if "_a_n" in name or "_b_n" in name or "_c_n" in name or ("voltage_a" in name and "voltage_ab" not in name):
            hh, h, l, ll = 242.0, 230.0, 210.0, 198.0
        else:
            hh, h, l, ll = 418.0, 399.0, 361.0, 342.0
    elif "current" in name:
        if "neutral" in name:
            hh, h, l, ll = 5.0, 3.0, 0.5, 0.1
        else:
            hh, h, l, ll = 80.0, 60.0, 10.0, 5.0
    elif "power" in name and "factor" not in name:
        hh, h, l, ll = 30000.0, 20000.0, 100.0, 10.0
    elif "power_factor" in name or "pf" in name:
        hh, h, l, ll = 1.0, 0.95, 0.8, 0.6
    elif "frequency" in name:
        hh, h, l, ll = 51.0, 50.5, 49.5, 49.0
    elif "cpu" in name:
        hh, h, l, ll = 70.0, 50.0, 1.0, 0.5
    elif "memory_usage" in name:
        hh, h, l, ll = 80.0, 70.0, 5.0, 2.0
    elif "disk_usage" in name or "hard_disk_usage" in name:
        hh, h, l, ll = 80.0, 70.0, 5.0, 2.0
    elif "disk_reading" in name or "disk_write" in name:
        hh, h, l, ll = 50.0, 20.0, 0.5, 0.1
    elif "speed" in name or "flow" in name or "uplink" in name or "downlink" in name:
        hh, h, l, ll = 10.0, 5.0, 0.5, 0.1
    elif "available_physical_memory" in name:
        hh, h, l, ll = 2000.0, 1500.0, 500.0, 200.0
    elif "remaining_disk" in name:
        hh, h, l, ll = 10.0, 8.0, 3.0, 1.5
    elif "total_size" in name or "total_physical" in name or name in ("ct", "pt"):
        hh, h, l, ll = float('inf'), float('inf'), float('-inf'), float('-inf')

    if hh is None:
        if avg > 0:
            hh, h, l, ll = round2(avg * 1.3), round2(avg * 1.1), round2(avg * 0.7), round2(avg * 0.3)
        elif mx > 0:
            hh, h, l, ll = round2(mx * 1.1), round2(mx * 1.0), round2(mn * 0.9), round2(mn * 0.5)
        else:
            hh, h, l, ll = 0.01, 0.001, -0.001, -0.01

    return (hh, h, l, ll)


def generate_bool_rule(alarm_name: str, point_name: str) -> dict:
    return {
        "alarm_name": alarm_name,
        "point_type": "BOOL",
        "boolean_value": "false",
        "boolean_severity": "Critical",
        "boolean_message": f"{point_name} state changed to false",
    }


def generate_numeric_rule(alarm_name: str, point_name: str, stats: PointStats) -> dict:
    hh, h, l, ll = get_numeric_thresholds(stats.name, stats.numeric_avg, stats.numeric_min, stats.numeric_max)
    return {
        "alarm_name": alarm_name,
        "point_type": "NUMERIC",
        "high_high_value": str(hh) if hh != float('inf') else "Infinity",
        "high_high_severity": "Critical",
        "high_high_message": f"{point_name} critically high: value >= {hh}" if hh != float('inf') else f"{point_name} critically high",
        "high_high_hysteresis": "0.00",
        "high_value": str(h) if h != float('inf') else "Infinity",
        "high_severity": "Major",
        "high_message": f"{point_name} high: value >= {h}" if h != float('inf') else f"{point_name} high",
        "high_hysteresis": "0.00",
        "low_value": str(l) if l != float('-inf') else "-Infinity",
        "low_severity": "Minor",
        "low_message": f"{point_name} low: value <= {l}" if l != float('-inf') else f"{point_name} low",
        "low_hysteresis": "0.00",
        "low_low_value": str(ll) if ll != float('-inf') else "-Infinity",
        "low_low_severity": "Warning",
        "low_low_message": f"{point_name} critically low: value <= {ll}" if ll != float('-inf') else f"{point_name} critically low",
        "low_low_hysteresis": "0.00",
    }


# ═══════════════════════════════════════════════════════════════════════
# 写入 MySQL
# ═══════════════════════════════════════════════════════════════════════

def write_to_mysql(bool_rules: list[dict], numeric_rules: list[dict]):
    conn = pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT, user=MYSQL_USER,
        password=MYSQL_PASSWORD, database=MYSQL_DB, charset="utf8mb4", autocommit=False
    )
    try:
        with conn.cursor() as cur:
            for r in bool_rules:
                cur.execute("""
                    INSERT INTO alarm_rule_boolean
                    (alarm_name, point_type, boolean_value, boolean_severity, boolean_message)
                    VALUES (%(alarm_name)s, %(point_type)s, %(boolean_value)s, %(boolean_severity)s, %(boolean_message)s)
                """, r)
            print(f"[INFO] 写入 {len(bool_rules)} 条 Boolean 规则")

            for r in numeric_rules:
                cur.execute("""
                    INSERT INTO alarm_rule_numeric
                    (alarm_name, point_type,
                     high_high_value, high_high_severity, high_high_message, high_high_hysteresis,
                     high_value, high_severity, high_message, high_hysteresis,
                     low_value, low_severity, low_message, low_hysteresis,
                     low_low_value, low_low_severity, low_low_message, low_low_hysteresis)
                    VALUES (%(alarm_name)s, %(point_type)s,
                            %(high_high_value)s, %(high_high_severity)s, %(high_high_message)s, %(high_high_hysteresis)s,
                            %(high_value)s, %(high_severity)s, %(high_message)s, %(high_hysteresis)s,
                            %(low_value)s, %(low_severity)s, %(low_message)s, %(low_hysteresis)s,
                            %(low_low_value)s, %(low_low_severity)s, %(low_low_message)s, %(low_low_hysteresis)s)
                """, r)
            print(f"[INFO] 写入 {len(numeric_rules)} 条 Numeric 规则")

        conn.commit()
        print("[INFO] 提交成功")
    except Exception as e:
        conn.rollback()
        print(f"[ERROR] 写入失败: {e}")
        raise
    finally:
        conn.close()


# ═══════════════════════════════════════════════════════════════════════
# 主函数
# ═══════════════════════════════════════════════════════════════════════

def main():
    print("=" * 60)
    print(" 报警规则自动生成迁移脚本")
    print(f" Log:   {LOG_FILE}")
    print(f" MySQL: {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DB}")
    print("=" * 60)

    # 1. 分析日志，获取所有唯一 path 的统计数据
    stats = analyze_log(LOG_FILE)
    print(f"[INFO] 日志中共 {len(stats)} 个唯一路径")

    # 2. 分类并生成规则
    bool_rules = []
    numeric_rules = []
    skipped_string = []

    # Track sequence numbers per point name (same name under different devices -> _00, _01, ...)
    seq_counters = defaultdict(int)

    for path in sorted(stats.keys()):
        point_stats = stats[path]
        point_name = path.split("/")[-1]
        ptype = point_stats.classify()

        if ptype == "STRING":
            skipped_string.append(f"{point_name} ({path})")
            continue

        # Assign sequence number
        seq = seq_counters[point_name]
        seq_counters[point_name] += 1
        alarm_name = f"{point_name}_{seq:02d}"

        if ptype == "BOOL":
            bool_rules.append(generate_bool_rule(alarm_name, point_name))
        elif ptype == "NUMERIC":
            numeric_rules.append(generate_numeric_rule(alarm_name, point_name, point_stats))

    print(f"[INFO] Boolean 规则: {len(bool_rules)} 条")
    print(f"[INFO] Numeric 规则: {len(numeric_rules)} 条")
    print(f"[INFO] String 类型跳过: {len(skipped_string)} 个点位")
    for s in skipped_string:
        print(f"       {s}")

    # 3. 预览
    for r in bool_rules[:3]:
        print(f"  [BOOL] {r['alarm_name']}: value={r['boolean_value']}, sev={r['boolean_severity']}")
    for r in numeric_rules[:3]:
        print(f"  [NUM]  {r['alarm_name']}: HH={r['high_high_value']}, H={r['high_value']}, L={r['low_value']}, LL={r['low_low_value']}")

    # 4. 写入 MySQL
    if bool_rules or numeric_rules:
        write_to_mysql(bool_rules, numeric_rules)
        print("[INFO] 完成")
    else:
        print("[INFO] 无规则需要写入")


if __name__ == "__main__":
    main()
