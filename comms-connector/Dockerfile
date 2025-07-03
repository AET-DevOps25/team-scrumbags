FROM gradle:8.14-jdk21 AS builder

WORKDIR /app
COPY . .

RUN ./gradlew clean build -x test

FROM eclipse-temurin:21
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]