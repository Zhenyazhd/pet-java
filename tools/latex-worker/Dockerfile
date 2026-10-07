# Build the runnable jar.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

# Fetch a pinned, statically linked tectonic for the target architecture.
FROM alpine:3.22 AS tectonic
ARG TARGETARCH
ARG TECTONIC_VERSION=0.17.0
RUN case "$TARGETARCH" in \
      amd64) arch=x86_64 ;; \
      arm64) arch=aarch64 ;; \
      *) echo "unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac \
 && wget -qO- "https://github.com/tectonic-typesetting/tectonic/releases/download/tectonic%40${TECTONIC_VERSION}/tectonic-${TECTONIC_VERSION}-${arch}-unknown-linux-musl.tar.gz" \
    | tar -xz -C /usr/local/bin tectonic

FROM eclipse-temurin:25-jre
COPY --from=tectonic /usr/local/bin/tectonic /usr/local/bin/tectonic

# Bake every package the resume template needs into the image: at runtime the worker
# compiles with --only-cached and has no network access.
# The cache is created by the same user that runs the worker, or tectonic cannot open it.
ENV XDG_CACHE_HOME=/opt/tectonic-cache
RUN mkdir /opt/tectonic-cache && chown 10001 /opt/tectonic-cache
USER 10001
COPY --chown=10001 src/main/resources/warmup.tex /tmp/warmup/warmup.tex
RUN cd /tmp/warmup && tectonic warmup.tex && rm -rf /tmp/warmup

COPY --from=build /src/target/latex-worker.jar /app/latex-worker.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/latex-worker.jar"]
