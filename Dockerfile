# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-17 AS compilacion
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy AS ejecucion

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl mariadb-client \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system federa \
    && useradd --system --gid federa --home-dir /app --shell /usr/sbin/nologin federa

WORKDIR /app
COPY --from=compilacion /workspace/target/backend-*.jar /app/backend.jar

RUN mkdir -p /data/almacenamiento /data/respaldos \
    && chown -R federa:federa /app /data

USER federa
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/backend.jar"]
