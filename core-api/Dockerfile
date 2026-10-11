FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/core-api-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java",  "-jar", "app.jar"]
