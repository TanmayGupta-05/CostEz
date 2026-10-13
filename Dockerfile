FROM node:24-alpine AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build
FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /source
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /ui/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && mkdir /app && chown 10001:10001 /app
WORKDIR /app
COPY --from=backend /source/target/costez-1.0.0.jar /app/costez.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --start-period=60s CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/costez.jar"]
