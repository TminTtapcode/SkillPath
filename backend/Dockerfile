FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system skillpath \
    && useradd --system --gid skillpath --home-dir /opt/skillpath skillpath
WORKDIR /opt/skillpath
COPY --from=build --chown=skillpath:skillpath /workspace/target/skillpath-backend-*.jar app.jar
USER skillpath
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/opt/skillpath/app.jar"]
