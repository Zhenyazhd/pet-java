FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/job-list-*.jar app.jar
# A number, not a name: Kubernetes can only check a numeric user against runAsNonRoot.
USER 10001
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]
