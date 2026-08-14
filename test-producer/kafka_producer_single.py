"""
Kafka Manual Producer - 手动发送单条点位数据到 Kafka

数据格式（五部分）：
  {"clientId":"...","mqttPath":"...","path":"...","timestamp":<毫秒时间戳>,"value":"..."}

- mqttPath: 点位唯一标识，用于 Flink keyBy 分区和 Redis 规则查找（匹配数据库 n4_address）
- path:     slot 路径，仅供引用，可从 mqttPath 自动推导
- type 不再由消息携带，改由后端根据 point_deployment.type 按 mqttPath 解析。

用法:
    pip install kafka-python
    python kafka_producer_single.py

交互方式:
    1. 输入要发送的 mqttPath（如 A1_MVP_A1/MVP_A1/MVP_A1_02_IRC_00/Dry_Mode）
    2. 输入要发送的 value
    3. 脚本自动推导 path，构造 JSON，时间戳设为当前时间，发送一条数据
"""

import json
import sys
import time
from datetime import datetime

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

# 点位数据来源标识，写入消息的 clientId 字段
CLIENT_ID = "clientId_1001"


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        消息构造                                     ║
# ╚═══════════════════════════════════════════════════════════════════╝

def derive_path(mqtt_path: str) -> str:
    """从 mqttPath 推导 slot path 格式。

    例: A1_MVP_A1/MVP_A1/MVP_A1_02_IRC_00/Dry_Mode
      -> slot:/Drivers/A1_MVP_A1/MVP_A1/points/MqttNetwork/MVP_A1_02_IRC_00/Dry_Mode

    规则: slot:/Drivers/ + 前两段 + /points/MqttNetwork/ + 剩余段
    若段数不足两段，则直接拼接 slot:/Drivers/ 前缀。
    """
    parts = mqtt_path.split("/")
    if len(parts) >= 2:
        head = "/".join(parts[:2])
        tail = "/".join(parts[2:])
        return f"slot:/Drivers/{head}/points/MqttNetwork/{tail}"
    return f"slot:/Drivers/{mqtt_path}"


def build_payload(mqtt_path: str, value: str) -> dict:
    """按固定格式构造消息体（五部分：clientId/mqttPath/path/timestamp/value）。"""
    return {
        "clientId": CLIENT_ID,
        "mqttPath": mqtt_path,
        "path": derive_path(mqtt_path),
        "timestamp": int(time.time() * 1000),
        "value": value,
    }


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        主逻辑                                       ║
# ╚═══════════════════════════════════════════════════════════════════╝

def main():
    print(f"[INFO] Kafka broker: {KAFKA_BROKER}")
    print(f"[INFO] Kafka topic : {KAFKA_TOPIC}")
    print(f"[INFO] clientId    : {CLIENT_ID}")
    print(f"[INFO] 数据格式: clientId / mqttPath / path / timestamp / value（无 type，由后端按 mqttPath 解析）")
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
            mqtt_path = input("\n请输入要发送的 mqttPath（直接回车退出）: ").strip()
            if not mqtt_path:
                print("[INFO] 退出")
                break

            value = input("请输入要发送的 value: ").strip()

            payload_dict = build_payload(mqtt_path, value)
            payload = json.dumps(payload_dict, ensure_ascii=False)

            future = producer.send(
                KAFKA_TOPIC,
                key=mqtt_path.encode("utf-8"),
                value=payload.encode("utf-8"),
            )
            # 等待发送结果，确保消息已提交
            metadata = future.get(timeout=10)

            print(f"[OK] 发送成功")
            print(f"     topic={metadata.topic}, partition={metadata.partition}, offset={metadata.offset}")
            print(f"     send_time={datetime.fromtimestamp(payload_dict['timestamp'] / 1000).strftime('%Y-%m-%d %H:%M:%S')}")
            print(f"     mqttPath={mqtt_path}")
            print(f"     path    ={payload_dict['path']}")
            print(f"     payload ={payload}")

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
