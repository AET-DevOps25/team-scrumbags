# TraceClient

## Table of Contents

- [Setup](#setup)
- [Code Scaffolding](#code-scaffolding)
  - [Views](#views)
  - [Components shared between views](#components-shared-between-views)
  - [Api services](#api-services)
  - [State management](#state-management)
  - [Additional Resources](#additional-resources)
- [CI/CD Pipeline](#cicd-pipeline)

## Setup

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

**PAY ATTENTION**: The client depends on Keycloak for authentication. Make sure to have a Keycloak server running and configured properly. The client is set up to connect to Keycloak at `http://localhost:7999` by default. You can change this in the [src/assets/env.ts](src/assets/env.js) file.

### Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Code scaffolding

This project uses Angular 20 with [angular material component library](https://material.angular.dev/components/categories) and [tailwind css](https://tailwindcss.com/docs) for layouts.

Angular CLI includes powerful code scaffolding tools to [generate components](https://angular.dev/cli/generate)

### Views

```bash
ng generate component views/name.view --standalone
```

### Components shared between views

```bash
ng generate component components/name.component --standalone
```

### Api services

```bash
ng generate service services/name.api
```

### State management

```bash
ng generate service states/name.state
```

### Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## CI/CD Pipeline

The CI/CD pipeline is set up to automatically build and test the SDLC Connector. It uses GitHub Actions to run the tests and build the java application on every PR to the main branch. The package action extends this functionality and additionally builds a Docker image and pushes it to the Docker Hub repository. This action is triggered on every push to the main branch.
