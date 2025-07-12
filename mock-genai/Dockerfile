FROM golang:1.24-alpine AS build-stage
WORKDIR /app

# Copy go mod and sum files
COPY go.mod go.sum ./
# Download all dependencies
RUN go mod download

# Copy the source code
COPY . .

# Set environment variables for cross-compilation
ENV CGO_ENABLED=0
ENV GOOS=linux
# We don't set GOARCH to allow for multi-architecture builds

# Build the application (will use the builder's architecture)
RUN go build -o mock-genai-app .

FROM gcr.io/distroless/base-debian12 AS build-release-stage

WORKDIR /app

# Copy the binary from the builder stage
COPY --from=build-stage /app/mock-genai-app .

# Expose port 3031
EXPOSE 3031

# Command to run the executable
CMD ["./mock-genai-app"]