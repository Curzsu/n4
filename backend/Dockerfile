# syntax=docker/dockerfile:1

# ---- Stage 1: build the fat jar with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy only the POM first to cache dependencies across source changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the source tree and build (skip tests; the CI/runtime env has no MySQL/Kafka)
COPY src ./src
RUN mvn -B clean package -DskipTests

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

# Extract the Spring Boot fat jar into a directory layout so that every dependency
# is a plain file under BOOT-INF/lib/*.jar. Flink's embedded local runtime uses its
# own classloader (InstantiationUtil) to deserialize scheduler config, and it cannot
# resolve classes from the nested-jar URL scheme (nested:...!/BOOT-INF/lib/...) that
# java -jar uses. A flat classpath of real files sidesteps this entirely.
COPY --from=builder /build/target/n4-monitor-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p app-extracted \
    && cd app-extracted \
    && jar xf ../app.jar \
    && rm ../app.jar

# Spring Boot exposes the API + WebSocket on 8089
EXPOSE 8089

# Build a flat classpath (BOOT-INF/classes + every jar in BOOT-INF/lib) and launch
# the Spring Boot main class directly with -cp instead of -jar. The nested-jar URL
# scheme (nested:...!/BOOT-INF/lib/...) used by java -jar is not understood by
# Flink's classloader (InstantiationUtil), which causes ClassNotFoundException
# for core Flink classes during JobMaster initialization. A flat classpath of
# real files sidesteps this entirely while keeping identical runtime behavior.
ENTRYPOINT ["sh", "-c", "CP=\"app-extracted/BOOT-INF/classes\"; for j in app-extracted/BOOT-INF/lib/*.jar; do CP=\"$CP:$j\"; done; exec java $JAVA_OPTS -cp \"$CP\" com.n4monitor.N4MonitorApplication"]
