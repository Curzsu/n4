# syntax=docker/dockerfile:1

# ---- Stage 1: build the standalone job jar with Maven (multi-module reactor) ----
# Build context is the REPOSITORY ROOT (see docker-compose.yml): building the
# monitor-flink-job module requires the parent POM and monitor-common. The
# monitor-backend POM is copied only for reactor parsing; its sources are not
# needed, so changes to backend code do not invalidate this image's cache.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy only the POMs first to cache dependencies across source changes
COPY pom.xml .
COPY monitor-common/pom.xml monitor-common/
COPY monitor-flink-job/pom.xml monitor-flink-job/
COPY monitor-backend/pom.xml monitor-backend/
RUN mvn -B -pl monitor-flink-job -am dependency:go-offline

# Copy only the sources needed by the reactor subset and build the shaded jar
COPY monitor-common/src monitor-common/src
COPY monitor-flink-job/src monitor-flink-job/src
RUN mvn -B -pl monitor-flink-job -am clean package -DskipTests

# ---- Stage 2: Flink runtime with the job baked in ----
# Application Mode base image; a jar under usrlib/ is on the classpath at
# startup, so the container starts the job via the compose command
# (standalone-job --job-classname ...).
FROM flink:2.2.1-scala_2.12-java21

# Asia/Shanghai timezone to keep job logs and processing-time displays aligned
# with the rest of the stack (base image defaults to UTC)
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*
ENV TZ=Asia/Shanghai

COPY --from=builder /build/monitor-flink-job/target/monitor-flink-job-0.0.1-SNAPSHOT-standalone.jar \
    /opt/flink/usrlib/alarm-job.jar
