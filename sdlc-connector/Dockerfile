# Stage 1: Build with Gradle
FROM gradle:8.5-jdk21 as builder

WORKDIR /app
COPY . .

RUN ./gradlew clean build -x test

# Stage 2: Run with JDK
FROM eclipse-temurin:21

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
