# The read-only API. Same shape as the collector image; see its comments.
#
#   docker build -f infra/api.Dockerfile -t bus-truth-api .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY common/pom.xml common/
COPY collector/pom.xml collector/
COPY processor/pom.xml processor/
COPY api/pom.xml api/
RUN mvn -B -q -pl api -am dependency:go-offline -DskipTests
COPY common/src common/src
COPY api/src api/src
RUN mvn -B -q -pl api -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bustruth && adduser -S bustruth -G bustruth
WORKDIR /app
COPY --from=build /src/api/target/api-*.jar app.jar
USER bustruth
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
