# Use official Eclipse Temurin Java 21 image
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests

# Run the jar file
ENTRYPOINT ["java", "-jar", "target/SMS-0.0.1-SNAPSHOT.jar"]