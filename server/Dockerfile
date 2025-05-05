FROM openjdk:21-slim

WORKDIR /app
COPY . .

RUN apt-get update && apt-get install -y findutils

RUN ./gradlew build -x test --no-daemon

CMD ["java", "-jar", "build/libs/*.jar"]
