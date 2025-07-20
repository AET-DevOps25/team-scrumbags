# Communication Integration (comms-connector) Microservice

This microservice is a Java Spring Boot app connects to external communication platforms' APIs, such as Discord. The messages from the added connections are pulled periodically, formatted, and sent to the gen AI microservice to be processed and queried in the Q&A and summarization functionalities.


## Table of Contents

- [Communication Integration (comms-connector) Microservice](#communication-integration-comms-connector-microservice)

    - [Table of Contents](#table-of-contents)
    - [Setup](#setup)
    - [Testing Endpoints](#testing-endpoints)
    - [Software Design](#software-design)
    - [Integration Tests](#integration-tests)
    - [CI/CD Pipeline](#cicd-pipeline)

## Setup

In order the use the build the microservice by itself:

- Copy the contents of `.env.example` into a new file with the name `.env`
- Fill in the Discord bot secret token and the bot ID (please contact the author to get these, as these are secret values)
- Run the docker compose file via `docker compose up -d`
- Test the API endpoints using the provided Bruno collection


## Testing Endpoints

After starting the comms-connector container, Swagger API docs can be viewed at http://localhost:9876/swagger-ui/index.html.

In the case that the whole project is run using `../docker-compose.local.yml`, then the docs can be accessed via http://localhost:8082/swagger-ui/index.html.

**For Discord:** A Discord bot has to be installed to the desired server first. This can be done using this URL and then following the prompts in the Discord app: https://discord.com/oauth2/authorize?client_id=1377229494263222302. Afterwards, the server ID required by the microservice can be accessed by:
- Navigating to Discord settings > Advanced > Enable Developer Mode
- Right-click the server icon of the server with the bot installed
- Click "Copy Server ID"

**Please note:** When using the message batch endpoint at `GET /projects/{projectId}/comms/{platform}/messages` the channel ID parameter corresponds to a Discord text channel ID from an added server connection and not the server ID itself.


## Software Design

The implementation follows a typical Spring Boot controller - service - repository - entity architecture. The app connects to a MySQL database `comms-db`. Added connections are saved in the `connections` table, which is iterated over when pulling messages. Platform users are saved in the `users` table, and TRACE users can assign TRACE UUIDs to each platform user, which is then passed over to gen AI as well.

Apart from just adding connections and saving users, the construction of the database tables allow the microservice to support:

- Multiple can be assigned platform accounts per TRACE user, if some users have multiple platform accounts
- Same platform channels can be assigned to different TRACE projects, if multiple projects use the same channels
- Platform users can exist with no TRACE UUID assigned, in case other platform users exist outside of the project members
- Matching channel / user IDs from different platforms can co-exist, and are not overwritten

While currently only Discord is supported as an external communication platform, the process of adding further platforms are simplified using interfaces that abstract the common functionality required from the model classes and REST clients that correspond to different platforms.

Upon starting the Spring Boot app, a separate thread is run to pull all new messages from all added connections, and then sleep until the next cycle. The thread can be stopped and a new thread started using API endpoints. As the supported platforms do not allow both live messages and also getting older messages using the same mechanisms, periodically pulling the messages was the optimal solution. Furthermore, this allows the app to do the most resource intensive tasks during low-usage times.


## Integration Tests

There are tests implemented that test each functionality of each endpoint separately. This is implemented using the Spring MVC test framework (`MockMvc`). External API calls (such as to Discord) are mocked so that testing is not dependent on these services.


## CI/CD Pipeline

The microservice image is automatically rebuilt and tested via Github Actions upon creating a pull request going into main. The actions for this microservice are only run in the case that the microservice directory `/comms-connector` actually has file changes.