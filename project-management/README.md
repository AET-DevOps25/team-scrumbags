# Setup 

## Local
1. `cp .env.local.example .env`
2. Install Docker and Docker Compose to run the database container (`docker compose up db -d`) or start a mysql database locally (please update the .env variables then).
3. configure the IntelliJ Run Config to read the `.env` file.
3.1 In IntelliJ, go to `Run` -> `Edit Configurations...`
3.2 Select your run configuration (e.g., `Spring Boot`).
3.3 Click `Modify options` -> `Environment Variables`.
3.4 Select the `.env` file you created in step 1.
4. Run the application using IntelliJ

## Docker
1. `cp .env.docker.example .env`
2. `docker compose up -d`
