# Use an official Maven image with Java 17 preinstalled
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the JAR (skipping tests to speed up)
RUN mvn clean package -DskipTests

# Use a smaller JDK image for running the app
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy only the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port Render will use
ENV PORT=8080
EXPOSE $PORT

# Start the Vert.x application
CMD ["java", "-jar", "app.jar"]
