# Multi-stage build for the Spring Boot backend.
#
# Stage 1 builds the jar with Maven against JDK 21 (matching pom.xml's <java.version>).
# Stage 2 copies only the built jar into a slim JRE image, so the final image doesn't carry
# Maven, the full JDK, or the downloaded dependency tree.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first and download dependencies into a separate layer, so a source-only change
# doesn't force re-downloading the whole dependency tree on the next build.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S hireflow && adduser -S hireflow -G hireflow
COPY --from=build /build/target/hireflow-*.jar /app/app.jar

# Create the résumé storage directory up front and hand both it and the jar to the runtime user.
# ResumeStorageService calls Files.createDirectories() on this path during bean construction, and
# /app itself is root-owned - without this the non-root user cannot create it, the bean throws,
# and the container exits before Tomcat ever binds a port. (Learned the hard way: the container
# failed to start in CI and the e2e job just timed out waiting for a backend that was never
# coming up.) Keep this path in sync with hireflow.resume.storage-dir in application.yml.
RUN mkdir -p /app/data/resumes \
    && chown -R hireflow:hireflow /app/app.jar /app/data
USER hireflow

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
