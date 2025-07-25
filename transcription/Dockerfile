FROM gradle:8.5-jdk21 AS java-builder
WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon

FROM python:3.12-slim AS python-builder
WORKDIR /app

ARG USE_WHISPERX=false
ENV USE_WHISPERX=${USE_WHISPERX}

COPY requirements.txt .
RUN apt-get update && apt-get install -y gcc g++ libsndfile1 ffmpeg \
 && pip install --upgrade pip setuptools wheel \
 && pip install --no-cache-dir --prefer-binary -r requirements.txt

RUN if [ "$USE_WHISPERX" = "true" ]; then \
        pip install --no-cache-dir --prefer-binary whisperx ; \
        else \
            echo "Skipping WhisperX install" ; \
        fi

RUN apt-get purge -y gcc g++ \
 && apt-get autoremove -y && apt-get clean && rm -rf /var/lib/apt/lists/*

FROM python:3.12-slim

# Install dependencies and ffmpeg
RUN apt-get update && apt-get install -y wget curl ffmpeg ca-certificates --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*

# Download and extract Temurin JDK 21 for the correct architecture
ARG TARGETARCH
RUN set -e; \
    if [ "$TARGETARCH" = "amd64" ]; then \
        JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz"; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
        JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.7_6.tar.gz"; \
    else \
        echo "Unsupported architecture: $TARGETARCH"; exit 1; \
    fi; \
    wget -qO /tmp/temurin.tar.gz "$JDK_URL" \
    && tar -xzf /tmp/temurin.tar.gz -C /usr/local --strip-components=1 \
    && rm /tmp/temurin.tar.gz

WORKDIR /app

COPY --from=python-builder /usr/local/lib/python3.12 /usr/local/lib/python3.12
COPY --from=python-builder /usr/local/bin /usr/local/bin
COPY --from=python-builder /usr/local/share /usr/local/share

COPY --from=java-builder /app/build/libs/*.jar app.jar
COPY transcriber.py .
COPY file-separator.wav .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]