FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM alpine:3.22 AS typst
ARG TARGETARCH
# Typst version and the sha256 of its two musl archives; bump together (CI pins the same version in ci.yml).
ARG TYPST_VERSION=0.15.1
ARG TYPST_SHA256_AMD64=a6d077d0a95eed5a2eba715b2dae06be954f624ccbf85758a03f389ded33118c
ARG TYPST_SHA256_ARM64=5aa8d74a3d906e60ea12a66ac2f37f8eef1b14cbad7182a745e393a10c23dcee
RUN apk add --no-cache xz \
 && case "$TARGETARCH" in \
      amd64) arch=x86_64; sha=$TYPST_SHA256_AMD64 ;; \
      arm64) arch=aarch64; sha=$TYPST_SHA256_ARM64 ;; \
      *) echo "unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac \
 && wget -qO /typst.tar.xz "https://github.com/typst/typst/releases/download/v${TYPST_VERSION}/typst-${arch}-unknown-linux-musl.tar.xz" \
 && echo "${sha}  /typst.tar.xz" | sha256sum -c - \
 && mkdir /typst \
 && tar -xJf /typst.tar.xz -C /typst \
 && cp /typst/typst-${arch}-unknown-linux-musl/typst /usr/local/bin/typst

FROM eclipse-temurin:25-jre
COPY --from=typst /usr/local/bin/typst /usr/local/bin/typst

USER 10001
COPY --from=build /src/target/pdf-worker.jar /app/pdf-worker.jar
# The fonts the CV templates ask for (all OFL, see fonts/OFL.md); typst only knows its own few without them.
COPY fonts /app/fonts
ENV TYPST_FONT_PATH=/app/fonts
EXPOSE 8090
# The JVM is PID 1 and does not reap killed compiler processes: a plain `docker run` needs `--init`
# (compose has `init: true`, Kubernetes uses shareProcessNamespace).
ENTRYPOINT ["java", "-jar", "/app/pdf-worker.jar"]
