# Multi-stage build for the full app: a single deployable that serves both the Spring Boot API
# and the built Angular app from one origin (see SpaWebConfig on the backend side).
#
# Stage 1 builds the Angular app with its own production configuration (apiUrl: '', relative to
# whatever origin serves it - see environment.production.ts).
# Stage 2 builds the Spring Boot jar with Maven against JDK 21 (matching pom.xml's
# <java.version>), copying stage 1's output into src/main/resources/static first so it ends up
# on the jar's classpath.
# Stage 3 copies only the built jar into a slim JRE image, so the final image doesn't carry
# Maven, Node, the full JDK, or either dependency tree.
#
# docker-compose.yml's separate `frontend` container (nginx, frontend/Dockerfile) is unaffected
# by this - it's a different image entirely, still built and run independently under the "full"
# profile. This Dockerfile just means the backend container can now also serve the app on its
# own, which is what the live Render deploy actually uses (a single web service, single URL).

# ---- Frontend build stage ----
FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm install
COPY frontend/. .
RUN npm run build

# ---- Backend build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first and download dependencies into a separate layer, so a source-only change
# doesn't force re-downloading the whole dependency tree on the next build.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
COPY --from=frontend-build /frontend/dist/frontend/browser ./src/main/resources/static
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
