#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

# Configuration
IMAGE_NAME="adiwave/intwork-hub-gateway"
TAG=$(date +%Y%m%d)-$(git rev-parse --short HEAD 2>/dev/null || echo "local")
DOCKERFILE_PATH="Dockerfile"

./gradlew clean

docker build \
    --no-cache \
    --pull \
    -t "$IMAGE_NAME:$TAG" \
    -t "$IMAGE_NAME:latest" \
    -f "$DOCKERFILE_PATH" .

echo "Build Complete: $IMAGE_NAME:$TAG"

docker images | grep intwork-hub-gateway

