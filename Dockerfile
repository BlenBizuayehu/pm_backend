# Use official Java 17 image (recommended for Vert.x)
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside container
WORKDIR /app

# Copy Maven project files
COPY pom.xml .
COPY src ./src

# Build the project using Maven
RUN ./mvnw clean package -DskipTests || mvn clean package -DskipTests

# Expose the port Render will use
ENV PORT=8080
EXPOSE $PORT

# Run the jar file (adjust the name if your jar name differs)
CMD ["java", "-jar", "target/your-app-name.jar"]
