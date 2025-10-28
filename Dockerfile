FROM maven:3.9.4-openjdk-17-slim

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the JAR
RUN mvn clean package -DskipTests

# Expose port (optional, Render will use $PORT)
EXPOSE 8080

# Run the app
CMD ["java", "-jar", "target/project-management-backend-1.0-SNAPSHOT.jar"]
