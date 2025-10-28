# 1. Use a Maven image with JDK 17
FROM maven:3.9.4-eclipse-temurin-17 AS build

# 2. Set working directory
WORKDIR /app

# 3. Copy pom.xml and download dependencies first (caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# 4. Copy source code
COPY src ./src

# 5. Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests

# 6. Use a smaller JDK image for running the app
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 7. Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# 8. Expose the port
EXPOSE 8080

# 9. Run the jar
CMD ["java", "-jar", "app.jar"]
