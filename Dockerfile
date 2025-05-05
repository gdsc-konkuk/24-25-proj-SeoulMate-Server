# Use a multi-architecture base image that supports ARM64
FROM openjdk:17

# Set the working directory in the container
WORKDIR /app

# Copy the executable jar file to the working directory
COPY build/libs/platform-core-*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Command to run the executable jar file
ENTRYPOINT ["java", "-jar", "app.jar"]

# To build
# docker buildx build --platform linux/amd64,linux/arm64 -t goldentrash/seoul-mate:latest . --push
