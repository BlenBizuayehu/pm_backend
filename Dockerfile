# Use Maven with Java 17
FROM maven:3.9.5-eclipse-temurin-17

# Set the working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Install dependencies (this caches them)
RUN mvn dependency:go-offline

# Expose the application port
EXPOSE 8080

# Start your Vert.x app using mvn exec:java
CMD ["mvn", "exec:java", "-Dexec.mainClass=com.example.MainVerticle"]
