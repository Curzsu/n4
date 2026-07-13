"""
将 Redis 中存储的各点位报警规则同步写入 MySQL alarm_rule 表。

Redis 数据源：
  - hash key : alarm:rules
  - field    : 点位完整路径 path（如 Drivers/.../Available_physical_memory）
  - value    : JSON 数组，每个元素是一条 AlarmRule：
      { level, value, severity, message, hysteresis, pointType }
    level 取值：Boolean / High / High-High / Low / Low-Low

写入 MySQL 的规则：
  - 同一个 path 下的所有 AlarmRule 合并成一行（宽表）。
    Boolean→boolean_* 字段，High→high_*，High-High→high_high_*，
    Low→low_*，Low-Low→low_low_*（各 4 列：value/severity/message/hysteresis）。
  - alarm_name 命名：点位名_序号（从 00 开始）。点位名 = path 最后一段（按 / 分隔）。
    同一点位名可能对应多个 path（不同设备下同名点位），每个 path 独立一行，
    序号按点位名分组、按 path 处理顺序递增。
  - alarm_level 字段记录该行包含的报警等级集合（逗号分隔），便于排查。
  - 同 alarm_name 冲突时覆盖更新（先删后插）。

用法：
    python sync_alarm_rules_to_mysql.py
    # 或指定参数
    python sync_alarm_rules_to_mysql.py --redis-host localhost --redis-port 6379 \
        --mysql-host localhost --mysql-port 3306 --mysql-user root \
        --mysql-password 1234 --mysql-db n4_monitor

依赖：pip install redis pymysql
"""

import argparse
import json
import sys
from collections import OrderedDict, defaultdict
from datetime import datetime

import pymysql
import redis


# ── 配置默认值（与 application.yaml 对齐）─────────────────────────────────
DEFAULT_REDIS_HOST = "localhost"
DEFAULT_REDIS_PORT = 6379
DEFAULT_REDIS_RULES_KEY = "alarm:rules"

DEFAULT_MYSQL_HOST = "localhost"
DEFAULT_MYSQL_PORT = 3306
DEFAULT_MYSQL_USER = "root"
DEFAULT_MYSQL_PASSWORD = "1234"
DEFAULT_MYSQL_DB = "n4_monitor"

# 报警等级 → (value/severity/message/hysteresis) 的列名前缀映射
# Boolean 无 hysteresis 列
LEVEL_COLUMN_MAP = {
    "Boolean":  ("boolean_value",  "boolean_severity",  "boolean_message",  None),
    "High":     ("high_value",      "high_severity",      "high_message",      "high_hysteresis"),
    "High-High":("high_high_value", "high_high_severity", "high_high_message", "high_high_hysteresis"),
    "Low":      ("low_value",       "low_severity",       "low_message",       "low_hysteresis"),
    "Low-Low":  ("low_low_value",   "low_low_severity",   "low_low_message",   "low_low_hysteresis"),
}

# MySQL 表的全部业务列（除 id / create_time / update_time / is_deleted）
ALL_COLUMNS = [
    "alarm_name", "point_type",
    "boolean_value", "boolean_severity", "boolean_message",
    "high_value", "high_severity", "high_message", "high_hysteresis",
    "high_high_value", "high_high_severity", "high_high_message", "high_high_hysteresis",
    "low_value", "low_severity", "low_message", "low_hysteresis",
    "low_low_value", "low_low_severity", "low_low_message", "low_low_hysteresis",
]


def extract_point_name(path: str) -> str:
    """从 path 中提取点位名：最后一段（按 / 分隔）。
    例：Drivers/ModbusTcpNetwork/LVP_A4/points/Collector/Available_physical_memory
        → Available_physical_memory
    """
    if not path:
        return ""
    # 兼容前导斜杠
    normalized = path.strip("/")
    parts = normalized.split("/")
    return parts[-1] if parts else ""


def load_rules_from_redis(redis_host: str, redis_port: int, rules_key: str) -> "OrderedDict[str, list]":
    """从 Redis hash 读取所有点位规则。
    返回 OrderedDict: path -> List[AlarmRule dict]，按 path 排序保证序号稳定。
    """
    r = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)
    try:
        r.ping()
    except redis.ConnectionError as e:
        print(f"[ERROR] 无法连接 Redis {redis_host}:{redis_port}: {e}")
        sys.exit(1)

    raw = r.hgetall(rules_key)
    if not raw:
        print(f"[WARN] Redis hash '{rules_key}' 为空，没有可同步的报警规则。")
        return OrderedDict()

    # 按 path 排序，保证同名点位的序号分配稳定可复现
    rules_by_path = OrderedDict()
    for path in sorted(raw.keys()):
        try:
            rules = json.loads(raw[path])
            if isinstance(rules, list) and rules:
                rules_by_path[path] = rules
        except json.JSONDecodeError as e:
            print(f"[WARN] path '{path}' 的规则 JSON 解析失败: {e}")

    print(f"[INFO] 从 Redis 读取到 {len(rules_by_path)} 个点位的报警规则。")
    return rules_by_path


def build_row(path: str, rules: list) -> dict:
    """把一个 path 下的多条 AlarmRule 合并成一行（宽表）。
    同一等级若有多条，后出现的覆盖前者（正常每等级最多一条）。
    point_name 仅用于后续 alarm_name 赋值，不写入表中。
    """
    row = {col: None for col in ALL_COLUMNS}
    row["alarm_name"] = ""        # 稍后按点位名分组赋序号
    row["_point_name"] = extract_point_name(path)  # 临时字段，不入库

    point_type = None
    for rule in rules:
        level = rule.get("level")
        if not level or level not in LEVEL_COLUMN_MAP:
            continue

        col_value, col_sev, col_msg, col_hyst = LEVEL_COLUMN_MAP[level]
        row[col_value] = rule.get("value")
        row[col_sev] = rule.get("severity")
        row[col_msg] = rule.get("message")
        if col_hyst is not None:
            row[col_hyst] = rule.get("hysteresis")

        # pointType 取规则中非空值（同一 path 下各规则应一致）
        if not point_type and rule.get("pointType"):
            point_type = rule.get("pointType")

    row["point_type"] = point_type
    return row


def assign_alarm_names(rows: list) -> None:
    """按 point_name 分组，给每行的 alarm_name 赋值：{point_name}_{NN}。
    序号从 00 开始，同一点位名下按 row 在列表中的顺序递增。
    """
    counter = defaultdict(int)
    for row in rows:
        name = row["_point_name"]
        seq = counter[name]
        row["alarm_name"] = f"{name}_{seq:02d}"
        counter[name] += 1


def write_to_mysql(rows: list, mysql_cfg: dict) -> int:
    """把行写入 MySQL alarm_rule 表。同 alarm_name 先删后插（覆盖）。"""
    conn = pymysql.connect(
        host=mysql_cfg["host"], port=mysql_cfg["port"],
        user=mysql_cfg["user"], password=mysql_cfg["password"],
        database=mysql_cfg["db"], charset="utf8mb4",
        autocommit=False,
    )
    inserted = 0
    try:
        with conn.cursor() as cur:
            # 清空旧数据（全量同步）。如需保留历史可改为按 alarm_name 逐条 upsert。
            cur.execute("DELETE FROM alarm_rule")

            cols_sql = ", ".join(f"`{c}`" for c in ALL_COLUMNS)
            placeholders = ", ".join(["%s"] * len(ALL_COLUMNS))
            insert_sql = f"INSERT INTO alarm_rule ({cols_sql}) VALUES ({placeholders})"

            for row in rows:
                values = [row[c] for c in ALL_COLUMNS]
                cur.execute(insert_sql, values)
                inserted += 1

        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"[ERROR] 写入 MySQL 失败: {e}")
        raise
    finally:
        conn.close()
    return inserted


def main():
    parser = argparse.ArgumentParser(description="同步 Redis 报警规则到 MySQL alarm_rule 表")
    parser.add_argument("--redis-host", default=DEFAULT_REDIS_HOST)
    parser.add_argument("--redis-port", type=int, default=DEFAULT_REDIS_PORT)
    parser.add_argument("--rules-key", default=DEFAULT_REDIS_RULES_KEY)
    parser.add_argument("--mysql-host", default=DEFAULT_MYSQL_HOST)
    parser.add_argument("--mysql-port", type=int, default=DEFAULT_MYSQL_PORT)
    parser.add_argument("--mysql-user", default=DEFAULT_MYSQL_USER)
    parser.add_argument("--mysql-password", default=DEFAULT_MYSQL_PASSWORD)
    parser.add_argument("--mysql-db", default=DEFAULT_MYSQL_DB)
    args = parser.parse_args()

    print(f"[INFO] 开始同步: {datetime.now():%Y-%m-%d %H:%M:%S}")
    print(f"[INFO] Redis: {args.redis_host}:{args.redis_port} hash={args.rules_key}")
    print(f"[INFO] MySQL: {args.mysql_host}:{args.mysql_port} db={args.mysql_db}")

    # 1. 读取 Redis 规则
    rules_by_path = load_rules_from_redis(args.redis_host, args.redis_port, args.rules_key)
    if not rules_by_path:
        print("[INFO] 无数据可同步，退出。")
        return

    # 2. 合并为宽表行
    rows = [build_row(path, rules) for path, rules in rules_by_path.items()]

    # 3. 分配 alarm_name
    assign_alarm_names(rows)

    # 打印前几条预览
    print("[INFO] 预览前 5 条:")
    for row in rows[:5]:
        print(f"  alarm_name={row['alarm_name']} | point_type={row['point_type']} | "
              f"point_name={row['_point_name']}")
    if len(rows) > 5:
        print(f"  ... 共 {len(rows)} 条")

    # 4. 写入 MySQL
    mysql_cfg = {
        "host": args.mysql_host, "port": args.mysql_port,
        "user": args.mysql_user, "password": args.mysql_password,
        "db": args.mysql_db,
    }
    inserted = write_to_mysql(rows, mysql_cfg)
    print(f"[INFO] 成功写入 {inserted} 条记录到 alarm_rule 表。")
    print(f"[INFO] 完成: {datetime.now():%Y-%m-%d %H:%M:%S}")


if __name__ == "__main__":
    main()
