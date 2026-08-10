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

# The fat jar name follows the default artifactId-version convention (no finalName in pom.xml)
COPY --from=builder /build/target/n4-monitor-0.0.1-SNAPSHOT.jar app.jar

# Spring Boot exposes the API + WebSocket on 8089
EXPOSE 8089

# Exec form so JAVA_OPTS is split into separate args by the shell entrypoint
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
