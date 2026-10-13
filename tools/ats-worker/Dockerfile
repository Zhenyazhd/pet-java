FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
USER 10001
COPY --from=build /src/target/ats-worker.jar /app/ats-worker.jar
EXPOSE 8091
ENTRYPOINT ["java", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "/app/ats-worker.jar"]
