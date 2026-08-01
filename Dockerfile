# Multi-stage build: fat jar with staged web SPA at /app/
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY pom.xml .
COPY core/pom.xml core/pom.xml
COPY service/pom.xml service/pom.xml
RUN mvn -pl service -am -DskipTests dependency:go-offline -q || true

COPY . .
RUN mvn -pl service -am -DskipTests package -q

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 10001 dm \
 && mkdir -p /data/saves /data/packs \
 && chown -R dm:dm /data /app

COPY --from=build /src/service/target/ai-dungeon-master-service-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    GAME_GUI_ENABLED=false \
    SERVER_PORT=8080 \
    GAME_SAVES_DIR=/data/saves

USER dm
EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \
  --server.port=${SERVER_PORT} \
  --game.gui.enabled=${GAME_GUI_ENABLED} \
  --game.saves.dir=${GAME_SAVES_DIR}"]
