"""
Kafka Manual Producer - 根据 path 从 points-2.log 查找模板，手动发送单条数据

用法:
    pip install kafka-python
    python kafka_manual_producer.py

交互方式:
    1. 输入要发送的 path（与 points-2.log 中的 key 一致）
    2. 输入要发送的 value
    3. 脚本会从日志中找到对应模板，填充其余字段，时间戳设为当前时间，发送一条数据
"""

import json
import sys
import time
from datetime import datetime
from pathlib import Path

# 允许控制台使用中文输出（Windows CMD 兼容）
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

try:
    from kafka import KafkaProducer
except ImportError:
    print("错误：未安装 kafka-python，请先执行 pip install kafka-python")
    sys.exit(1)


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        配置                                         ║
# ╚═══════════════════════════════════════════════════════════════════╝

KAFKA_BROKER = "47.121.30.136:9092"
KAFKA_TOPIC = "obix-mqtt-points"
LOG_FILE = str(Path(__file__).parent.parent / "材料" / "points-2.log")


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        日志解析                                     ║
# ╚═══════════════════════════════════════════════════════════════════╝

import re

LINE_PATTERN = re.compile(
    r'timestamp=\[([^\]]+)\]\s*,topic=([^,]+)\s*,key=(.*?)\s*,value=(.*)'
)


def parse_log_line(line: str) -> dict | None:
    """解析单条日志行，返回模板字典（含 key 和 value JSON）。"""
    line = line.strip()
    if not line:
        return None

    m = LINE_PATTERN.match(line)
    if not m:
        return None

    key = m.group(3).strip()
    value_str = m.group(4).strip()

    try:
        json.loads(value_str)
    except json.JSONDecodeError:
        return None

    return {"key": key, "value": value_str}


def load_templates(log_path: str) -> dict[str, dict]:
    """加载日志，按 path（即 key）建立模板索引。"""
    templates = {}
    path_obj = Path(log_path)
    if not path_obj.exists():
        print(f"错误：日志文件不存在: {path_obj}")
        sys.exit(1)

    with open(path_obj, "r", encoding="utf-8") as f:
        for line in f:
            parsed = parse_log_line(line)
            if not parsed:
                continue
            # 以 key（path）作为索引；若日志中有重复 path，保留第一次出现的模板
            if parsed["key"] not in templates:
                try:
                    templates[parsed["key"]] = json.loads(parsed["value"])
                except json.JSONDecodeError:
                    pass

    return templates


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        主逻辑                                       ║
# ╚═══════════════════════════════════════════════════════════════════╝

def main():
    print(f"[INFO] 正在加载日志模板: {LOG_FILE}")
    templates = load_templates(LOG_FILE)
    print(f"[INFO] 共加载 {len(templates)} 个不同 path 的模板")
    print(f"[INFO] Kafka broker: {KAFKA_BROKER}")
    print(f"[INFO] Kafka topic : {KAFKA_TOPIC}")
    print("-" * 60)

    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BROKER,
            request_timeout_ms=10000,
        )
    except Exception as e:
        print(f"错误：连接 Kafka 失败: {e}")
        sys.exit(1)

    while True:
        try:
            path = input("\n请输入要发送的 path（直接回车退出）: ").strip()
            if not path:
                print("[INFO] 退出")
                break

            if path not in templates:
                print(f"错误：未在日志中找到 path: {path}")
                print("提示：可输入日志中存在的完整 path，例如:")
                # 列出前 5 个示例
                for example in list(templates.keys())[:5]:
                    print(f"  - {example}")
                continue

            value = input("请输入要发送的 value: ").strip()

            template = templates[path].copy()
            template["value"] = value
            template["timestamp"] = int(time.time() * 1000)

            # 如果模板里没有 path（理论上都有），兜底补一个
            if "path" not in template or not template["path"]:
                template["path"] = path

            payload = json.dumps(template, ensure_ascii=False)

            future = producer.send(
                KAFKA_TOPIC,
                key=path.encode("utf-8"),
                value=payload.encode("utf-8"),
            )
            # 等待发送结果，确保消息已提交
            metadata = future.get(timeout=10)

            print(f"[OK] 发送成功")
            print(f"     topic={metadata.topic}, partition={metadata.partition}, offset={metadata.offset}")
            print(f"     send_time={datetime.fromtimestamp(template['timestamp'] / 1000).strftime('%Y-%m-%d %H:%M:%S')}")
            print(f"     payload={payload}")

        except KeyboardInterrupt:
            print("\n[INFO] 用户中断，退出")
            break
        except Exception as e:
            print(f"[ERR] 发送失败: {e}")

    producer.flush(timeout=10)
    producer.close(timeout=5)
    print("[INFO] 已关闭 Kafka 生产者")


if __name__ == "__main__":
    main()
