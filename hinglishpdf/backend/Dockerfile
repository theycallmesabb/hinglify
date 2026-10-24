FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Python 3, venv, and dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-venv \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/storage /app/extractor

# Set up Python virtual environment and install PyMuPDF + FastAPI
COPY extractor/requirements.txt /app/extractor/requirements.txt
RUN python3 -m venv /opt/venv && \
    /opt/venv/bin/pip install --no-cache-dir -r /app/extractor/requirements.txt

COPY extractor/main.py /app/extractor/main.py

ENV PATH="/opt/venv/bin:$PATH"

COPY --from=build /app/target/hinglishpdf-backend-1.0.0-SNAPSHOT.jar app.jar

# Entrypoint script: starts Python extractor on 127.0.0.1:8000 in background, then starts Spring Boot on 8080
RUN printf '#!/bin/sh\nuvicorn main:app --app-dir /app/extractor --host 127.0.0.1 --port 8000 &\nexec java -jar app.jar\n' > /app/start.sh && chmod +x /app/start.sh

EXPOSE 8080

ENTRYPOINT ["/app/start.sh"]
