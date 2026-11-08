"""Create private loopback-only lab credentials without replacing existing files."""
import secrets
from common import ROOT

def main():
    target = ROOT / ".env.local"
    if target.exists():
        print("Existing .env.local retained.")
        return
    mysql, redis, neo4j = [secrets.token_urlsafe(24) for _ in range(3)]
    values = {
        "MYSQL_URL": "jdbc:mysql://127.0.0.1:23306/n4_monitor?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "MYSQL_USERNAME": "root", "MYSQL_PASSWORD": mysql,
        "REDIS_HOST": "127.0.0.1", "REDIS_PORT": "16379", "REDIS_PASSWORD": redis,
        "NEO4J_URI": "bolt://127.0.0.1:17687", "NEO4J_HTTP_URL": "http://127.0.0.1:17474",
        "NEO4J_USERNAME": "neo4j", "NEO4J_PASSWORD": neo4j, "NEO4J_AUTH": "neo4j/" + neo4j,
        "TDENGINE_ENABLED": "false", "TDENGINE_URL": "jdbc:TAOS-RS://127.0.0.1:16041/n4",
        "TDENGINE_USERNAME": "root", "TDENGINE_PASSWORD": "unused-local-disabled",
        "ALARM_FLINK_ENABLED": "false", "KAFKA_BOOTSTRAP_SERVERS": "127.0.0.1:19092",
        "FUXA_ENABLED": "false", "FUXA_BASE_URL": "http://127.0.0.1:11881",
        "SERVER_PORT": "8088", "VITE_BACKEND_PORT": "8088",
        "JWT_SECRET": secrets.token_urlsafe(48)
    }
    with target.open("x", encoding="utf-8") as stream:
        stream.write("# Private local-only lab configuration. Never commit.\n")
        stream.write("\n".join(key + "=" + value for key, value in values.items()) + "\n")
    print("Created .env.local with independent local credentials.")

if __name__ == "__main__":
    main()
