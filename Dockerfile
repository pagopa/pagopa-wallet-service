FROM amazoncorretto:21-alpine@sha256:6a98c4402708fe8d16e946b4b5bac396379ec5104c1661e2a27b2b45cf9e2d16 AS build
WORKDIR /workspace/app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle .
COPY gradle.lockfile .
COPY gradle.properties .

COPY eclipse-style.xml eclipse-style.xml
COPY npg-api npg-api
COPY api-spec api-spec
COPY src src
RUN ./gradlew build -x test
RUN mkdir build/extracted && java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted

FROM amazoncorretto:21-alpine@sha256:6a98c4402708fe8d16e946b4b5bac396379ec5104c1661e2a27b2b45cf9e2d16

RUN addgroup --system user && adduser --ingroup user --system user
USER user:user

WORKDIR /app/

ARG EXTRACTED=/workspace/app/build/extracted

#ELK Agent
ADD --chown=user https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.2.0/opentelemetry-javaagent.jar .

COPY --from=build --chown=user ${EXTRACTED}/dependencies/ ./
RUN true
COPY --from=build --chown=user ${EXTRACTED}/spring-boot-loader/ ./
RUN true
COPY --from=build --chown=user ${EXTRACTED}/snapshot-dependencies/ ./
RUN true
COPY --from=build --chown=user ${EXTRACTED}/application/ ./
RUN true


ENTRYPOINT ["java","-javaagent:opentelemetry-javaagent.jar","org.springframework.boot.loader.launch.JarLauncher"]

