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
RUN go build -o mock-notes-app .

FROM gcr.io/distroless/base-debian12 AS build-release-stage

WORKDIR /app

# Copy the binary from the builder stage
COPY --from=build-stage /app/mock-notes-app .

# Expose port 3030
EXPOSE 3030

# Command to run the executable
CMD ["./mock-notes-app"]