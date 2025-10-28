# Use an OpenJDK image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the jar
RUN mvn clean package -DskipTests

# Expose the port (not strictly required, but good practice)
EXPOSE 8080

# Run the app
CMD ["java", "-jar", "target/project-management-backend-1.0-SNAPSHOT.jar"]
