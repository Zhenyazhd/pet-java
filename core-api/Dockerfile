# Build the runnable jar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

# Run as a non-root user on a JRE-only image.
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/core-api-*.jar app.jar
USER app
EXPOSE 8080
# Liveness/readiness: /actuator/health/liveness and /actuator/health/readiness.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
