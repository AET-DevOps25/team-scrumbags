FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon

FROM eclipse-temurin:21

WORKDIR /app
COPY requirements.txt .

# Install Python, pip, venv, and build tools
RUN apt-get update && apt-get install -y \
    python3 python3-pip python3-venv \
    gcc make libpython3-dev \
    && rm -rf /var/lib/apt/lists/*

# Create a virtual environment
RUN python3 -m venv /opt/venv

# Activate venv and install JEP
RUN . /opt/venv/bin/activate && \
    pip install --upgrade pip && \
    pip install -r requirements.txt

COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]