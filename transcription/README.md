# Transcription (transcription) Microservice

This microservice is a Java Spring Boot app that is used to transcribe meeting notes (both video and text) into a format that can be processed by the GenAI service. This also includes extracting the speakers, based on configured speaker samples. The transcription text is then forwarded to the GenAI Service for processing it and using it for the summarization and question answering.


## Table of Contents

- [Transcription (transcription) Microservice](#transcription-transcription-microservice)

  - [Table of Contents](#table-of-contents)
  - [Setup](#setup)
  - [Testing Endpoints (Swagger Documentation)](#testing-endpoints-swagger-documentation)
  - [Software Design](#software-design)
  - [Integration Tests](#integration-tests)
  - [CI/CD Pipeline](#cicd-pipeline)

## Setup

In order the use the build the microservice by itself:

- Copy the contents of `.env.example` into a new file with the name `.env`
- Fill in the `.env` file with the required values, specifically setting `ASSEMBLYAI_API_KEY` and `HF_TOKEN`. Contact the project maintainers if you do not have access to these values.
- Run the docker compose file via `docker compose up -d`
- Test the API endpoints using the provided Bruno collection


## Testing Endpoints (Swagger Documentation)

After starting the transcription container, Swagger API docs can be viewed at http://localhost:4269/swagger-ui/index.html.

In the case that the whole project is run using `../docker-compose.local.yml`, then the docs can be accessed via http://localhost:8083/swagger-ui/index.html.

**Please note:** When uploading user samples, please make sure that they are at least 15 seconds long and in one of the following formats:
- .mp3, .wav, .m4a, .flac, .aac, .ogg, .mp4, .mov, .opus

## Software Design

The implementation follows a typical Spring Boot MVC architecture. The app connects to a MySQL database `transcription-db`.

There are two Controllers, the SpeakerController and the TranscriptionController, which handle the endpoints for managing speaker samples and transcribing meeting notes, respectively. The SpeakerController allows users to upload audio samples of their voice, which are then stored in the database and used to identify speakers in the meeting notes. The TranscriptionController handles the transcription of meeting notes, either from audio or video files.
The transcription process uses the AssemblyAI API to transcribe audio and video files, and the transcribed text is then stored in the database. The transcription service also extracts speaker information from the transcribed text based on the uploaded speaker samples.
The database schema is designed to store
- Speaker samples, which include the project ID, speaker's name, UUID, and the audio file used for training the speaker model.
- Transcriptions, which include the transcription ID, project ID, user ID, file name, transcription text, and the status of the transcription job.

Transcription jobs are created when a user uploads a meeting note file, and the transcription process is initiated. The status of the transcription job is updated as the transcription progresses, and once completed, the transcribed text is stored in the database.
The transcription jobs use a separate thread to handle the transcription process asynchronously, allowing the user to continue using the application while the transcription is being processed.
The transcription service also includes error handling and logging to ensure robustness and traceability.

## Integration Tests

There are tests implemented that test each functionality of each endpoint separately. This is implemented using the Spring MVC test framework `MockMvc` and `MockitoBean`.


## CI/CD Pipeline

The microservice image is automatically rebuilt and tested via Github Actions upon creating a pull request going into main. The actions for this microservice are only run in the case that the microservice directory `/transcription` actually has file changes.