FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon

FROM eclipse-temurin:21

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
    pip install setuptools numpy && \
    pip install --no-cache-dir --no-build-isolation jep

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
COPY scripts/ /app/scripts/

# Expose jep native library
RUN ln -s $(python3 -c "import site; print(site.getsitepackages()[0])")/jep /usr/lib/jep

ENV PYTHONPATH=/app/scripts
ENV LD_LIBRARY_PATH=/opt/venv/lib/python3.12/site-packages/jep
ENV JAVA_OPTS="-Djava.library.path=/opt/venv/lib/python3.12/site-packages/jep"

RUN find / -name "libjep.so"

CMD ["sh", "-c", "java $java_opts -jar app.jar"]