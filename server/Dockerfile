FROM openjdk:21
WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon
CMD ["java", "-jar", "build/libs/*.jar"]
