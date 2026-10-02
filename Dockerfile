# Build the Vite app first; its dist/ output is copied into Spring Boot's static resources.
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_API_BASE_URL=/api
ARG VITE_TENANT_ID
ARG VITE_CAMPUS_ID
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_TENANT_ID=${VITE_TENANT_ID}
ENV VITE_CAMPUS_ID=${VITE_CAMPUS_ID}
RUN npm run build

# Package the frontend assets together with the Spring Boot application.
FROM eclipse-temurin:21-jdk-alpine AS app-build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN ./mvnw package -DskipTests

# Run the packaged app; the embedded index and assets are served from the same origin as /api.
ENTRYPOINT ["java", "-jar", "target/SMS-0.0.1-SNAPSHOT.jar"]