"""
Kafka Producer - Simulates device point data from points-2.log

Reads the log file line by line, extracts the JSON payload from the 'value' field,
and sends each record to a Kafka topic at a configurable interval.

Usage:
    pip install kafka-python
    python kafka_producer.py
"""

import json
import time
import re
import signal
import sys
from pathlib import Path

try:
    from kafka import KafkaProducer
except ImportError:
    print("Error: kafka-python not installed. Run: pip install kafka-python")
    sys.exit(1)


# ╔═══════════════════════════════════════════════════════════════════╗
# ║                        CONFIGURATION                              ║
# ╚═══════════════════════════════════════════════════════════════════╝

# Kafka broker address
KAFKA_BROKER = "47.121.30.136:9092"

# Kafka topic to send messages to
KAFKA_TOPIC = "obix-mqtt-points"

# Path to the log data file
LOG_FILE = str(Path(__file__).parent.parent / "材料" / "points-2.log")

# Seconds between each message (1.0 = one message per second)
SEND_INTERVAL = 2

# Whether to loop continuously after reaching the end of the file
LOOP = True

# Whether to update the JSON 'timestamp' field to current time (simulates real device)
UPDATE_TIMESTAMP = True


# ── Graceful shutdown ──────────────────────────────────────────────
running = True

def signal_handler(sig, frame):
    global running
    print("\n[INFO] Shutting down...")
    running = False

signal.signal(signal.SIGINT, signal_handler)


# ── Line parser ────────────────────────────────────────────────────
LINE_PATTERN = re.compile(
    r'timestamp=\[([^\]]+)\]\s*,topic=([^,]+)\s*,key=(.*?)\s*,value=(.*)'
)

def parse_log_line(line: str) -> dict | None:
    """
    Parse a single log line into its components.

    Expected format:
        timestamp=[2026-07-03 15:46:08.740] ,topic=obix-mqtt-points ,key=<path> ,value=<json>

    Returns dict with keys: original_timestamp, topic, key, value (as JSON string)
    """
    line = line.strip()
    if not line:
        return None

    m = LINE_PATTERN.match(line)
    if not m:
        return None

    timestamp_str = m.group(1).strip()
    topic = m.group(2).strip()
    key = m.group(3).strip()
    value_str = m.group(4).strip()

    # The value field is already valid JSON starting with {
    try:
        json.loads(value_str)
    except json.JSONDecodeError as e:
        print(f"  [WARN] Invalid JSON on line, skipping: {e}")
        return None

    return {
        "original_timestamp": timestamp_str,
        "topic": topic,
        "key": key,
        "value": value_str,
    }


def infer_type(value: str) -> str:
    """Infer the point type from its value: boolean | numeric | string."""
    if not value:
        return "string"
    v = value.strip().lower()
    if v in ("true", "false"):
        return "boolean"
    try:
        float(v)
        return "numeric"
    except ValueError:
        return "string"


# ── Main producer loop ─────────────────────────────────────────────
def run():
    log_path = Path(LOG_FILE)
    if not log_path.exists():
        print(f"Error: Log file not found: {log_path}")
        sys.exit(1)

    # Read and pre-parse all lines
    with open(log_path, "r", encoding="utf-8") as f:
        raw_lines = f.readlines()

    records = []
    for line in raw_lines:
        parsed = parse_log_line(line)
        if parsed:
            records.append(parsed)

    total = len(records)
    print(f"[INFO] Loaded {total} valid records from {log_path}")
    print(f"[INFO] Kafka broker : {KAFKA_BROKER}")
    print(f"[INFO] Kafka topic  : {KAFKA_TOPIC}")
    print(f"[INFO] Interval     : {SEND_INTERVAL}s")
    print(f"[INFO] Loop         : {LOOP}")
    print(f"[INFO] Update TS    : {UPDATE_TIMESTAMP}")
    print("-" * 60)

    # Create Kafka producer
    try:
        producer = KafkaProducer(
            bootstrap_servers=KAFKA_BROKER,
            request_timeout_ms=10000,
        )
    except Exception as e:
        print(f"Error: Failed to connect to Kafka broker: {e}")
        sys.exit(1)

    print("[INFO] Connected to Kafka broker successfully")
    print("[INFO] Starting to send messages... (Ctrl+C to stop)\n")

    sent = 0
    errors = 0
    round_num = 0

    while running:
        round_num += 1
        if LOOP and round_num > 1:
            print(f"\n[INFO] === Starting round {round_num} ===")

        for i, record in enumerate(records):
            if not running:
                break

            value_str = record["value"]

            # Optionally update the timestamp to current time
            if UPDATE_TIMESTAMP:
                try:
                    obj = json.loads(value_str)
                    obj["timestamp"] = int(time.time() * 1000)
                    # Infer and set type from value
                    obj["type"] = infer_type(str(obj.get("value", "")))
                    value_str = json.dumps(obj, ensure_ascii=False)
                except Exception:
                    pass

            try:
                future = producer.send(
                    KAFKA_TOPIC,
                    key=record["key"].encode("utf-8"),
                    value=value_str.encode("utf-8"),
                )
                future.add_callback(
                    lambda meta, idx=sent: None
                )
                future.add_errback(
                    lambda exc, idx=sent: print(f"  [ERR] Send failed: {exc}")
                )
                sent += 1

                # Progress output every 10 messages
                if sent % 10 == 0 or sent == 1:
                    pct = (i + 1) / total * 100
                    print(
                        f"  [{sent}/{total}] ({pct:.1f}%) key={record['key'][:60]}"
                    )

            except Exception as e:
                errors += 1
                print(f"  [ERR] #{sent + errors}: {e}")

            time.sleep(SEND_INTERVAL)

        if not LOOP or not running:
            break

    # Flush and close
    print(f"\n[INFO] Flushing remaining messages...")
    producer.flush(timeout=10)
    producer.close(timeout=5)

    print("-" * 60)
    print(f"[INFO] Done. Sent: {sent}, Errors: {errors}, Rounds: {round_num}")


if __name__ == "__main__":
    run()
