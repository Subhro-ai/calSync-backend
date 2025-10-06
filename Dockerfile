# Use a lightweight Java runtime as a base image
FROM eclipse-temurin:17-jdk-alpine

# Set a volume for temporary files
VOLUME /tmp

# Copy the packaged JAR file into the container
COPY target/calsync-0.0.1-SNAPSHOT.jar app.jar

# Specify the command to run your application
ENTRYPOINT ["java","-jar","/app.jar"]