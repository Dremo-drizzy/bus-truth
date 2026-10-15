# The collector: polls the three GTFS-RT feeds, archives the raw bytes, writes rows.
#
# Build context is the repository root, because the collector depends on the common
# module and Maven needs the whole reactor:
#   docker build -f infra/collector.Dockerfile -t bus-truth-collector .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Poms first, so a source-only change reuses the cached dependency download. Maven
# resolves the whole reactor, so every module's pom has to be present.
COPY pom.xml ./
COPY common/pom.xml common/
COPY collector/pom.xml collector/
COPY processor/pom.xml processor/
COPY api/pom.xml api/
RUN mvn -B -q -pl collector -am dependency:go-offline -DskipTests
COPY common/src common/src
COPY collector/src collector/src
RUN mvn -B -q -pl collector -am package -DskipTests

# A JRE, not the JDK, and not the build image: the result is about a tenth the size
# and carries no compiler or Maven cache into production.
FROM eclipse-temurin:21-jre-alpine
# Never root. A process that only needs to write one archive directory has no reason
# to be able to write anything else.
RUN addgroup -S bustruth && adduser -S bustruth -G bustruth
WORKDIR /app
COPY --from=build /src/collector/target/collector-*.jar app.jar
# The archive is a mounted volume in production; this is where the container expects
# it, and the owner has to be the user that writes it.
RUN mkdir -p /data/raw && chown -R bustruth:bustruth /data
USER bustruth
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
