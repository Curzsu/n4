# syntax=docker/dockerfile:1

# ---- Stage 1: build the fat jar with Maven (multi-module reactor) ----
# Build context is the REPOSITORY ROOT (see docker-compose.yml) so that all
# three Maven modules (monitor-common, monitor-flink-job, monitor-backend)
# are available to the reactor build.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy only the POMs first to cache dependencies across source changes
COPY pom.xml .
COPY monitor-common/pom.xml monitor-common/
COPY monitor-flink-job/pom.xml monitor-flink-job/
COPY monitor-backend/pom.xml monitor-backend/
RUN mvn -B dependency:go-offline

# Copy the source trees and build (skip tests; the CI/runtime env has no MySQL/Kafka)
COPY monitor-common/src monitor-common/src
COPY monitor-flink-job/src monitor-flink-job/src
COPY monitor-backend/src monitor-backend/src
RUN mvn -B clean package -DskipTests

# Extract the fat jar into a directory layout here (the JDK 'jar' tool is available
# in this builder stage). The runtime stage (JRE only) has no 'jar' command, so we
# extract now and copy the directory over. A flat layout of BOOT-INF/classes +
# BOOT-INF/lib/*.jar gives Flink's classloader standard file:// URLs it can load
# from, avoiding the nested-jar URL issue (nested:...!/BOOT-INF/lib/...) that breaks
# Flink's InstantiationUtil when running via java -jar.
RUN mkdir -p /app-extracted \
    && cd /app-extracted \
    && jar xf /build/monitor-backend/target/monitor-backend.jar

# ---- Stage 2: runtime JRE ----
FROM eclipse-temurin:21-jre

# Asia/Shanghai timezone to match serverTimezone=Asia/Shanghai and TDengine logic
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -Dfile.encoding=UTF-8"

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the extracted fat jar layout (classes + dependencies as plain files)
COPY --from=builder /app-extracted ./app-extracted

# Spring Boot exposes the API + WebSocket on 8089
# Backend port is injected at runtime via SERVER_PORT (default 8088); EXPOSE is
# informational only and has no effect in host network mode.
EXPOSE 8088

# Liveness probe: TCP-connect to the API port. Spring Boot has no actuator
# endpoint, so a port-open check is the cheapest reliable signal that the JVM is
# up and listening. Reads $SERVER_PORT (injected by compose, default 8088) so the
# probe follows the actual listener instead of a hardcoded port.
# bash + /dev/tcp avoids installing curl in the runtime image.
# Non-fatal if the shell feature were unavailable (Docker would only mark the
# container unhealthy; restart:unless-stopped still handles real crashes).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "</dev/tcp/127.0.0.1/${SERVER_PORT:-8088} || exit 1"]

# Build a flat classpath (BOOT-INF/classes + every jar in BOOT-INF/lib) and launch
# the Spring Boot main class directly with -cp instead of -jar. The nested-jar URL
# scheme (nested:...!/BOOT-INF/lib/...) used by java -jar is not understood by
# Flink's classloader (InstantiationUtil), which causes ClassNotFoundException
# for core Flink classes during JobMaster initialization. A flat classpath of
# real files sidesteps this entirely while keeping identical runtime behavior.
ENTRYPOINT ["sh", "-c", "CP=\"app-extracted/BOOT-INF/classes\"; for j in app-extracted/BOOT-INF/lib/*.jar; do CP=\"$CP:$j\"; done; exec java $JAVA_OPTS -cp \"$CP\" com.n4monitor.N4MonitorApplication"]
