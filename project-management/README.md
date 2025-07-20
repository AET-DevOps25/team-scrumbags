# Project Management

The project management microservice

## Table of Contents

- [Software Development Life Cycle (SDLC) Connector](#software-development-life-cycle-sdlc-connector)

  - [Table of Contents](#table-of-contents)
  - [Setup](#setup)
  - [API Documentation](#api-documentation)
  - [Software Design](#software-design)
  - [Integration Tests](#integration-tests)
  - [CI/CD Pipeline](#cicd-pipeline)
  - [Github Webhook Integration Guide](#github-webhook-integration-guide)

## Setup

The project management microservice can be run using `docker compose`:

```bash
# this will start all services with default values
docker compose up -d
```

If you want to adjust the configuration, you can create a `.env` file in the `project-management` directory based on the `.env.local.example` file. The `.env` file will override the default values in the `docker-compose.yml` file.

## API Documentation

Swagger API docs can be viewed at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) after microservice start.

## Software Design
The project management microservice is designed to be modular and extensible. It uses a typical Spring Boot architecture with controllers, services, repositories, and entities. The microservice handles project management tasks such as creating projects and managing users in the projects.

## Integration Tests

There are tests implemented that test the functionality of each endpoint separately. This is implemented using the Spring MVC test framework (`MockMvc`).

## CI/CD Pipeline

The CI/CD pipeline is set up to automatically build and test the Project Management Microservice. It uses GitHub Actions to run the tests and build the java application on every PR to the main branch. The package action extends this functionality and additionally builds a Docker image and pushes it to the Docker Hub repository. This action is triggered on every push to the main branch.
