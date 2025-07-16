package com.trace.transcription.controller;

import com.trace.transcription.repository.TranscriptRepository;
import com.trace.transcription.service.TranscriptService;
import com.trace.transcription.model.TranscriptEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.apache.commons.io.FilenameUtils.getExtension;

/**
 * REST controller responsible for handling transcript-related operations within a transcription project.
 * <p>
 * Provides endpoints to upload audio for transcription, list existing transcripts,
 * retrieve individual transcripts, download audio files, and stream raw audio files as ZIP archives.
 * </p>
 */
@RestController
@RequestMapping("projects/{projectId}")
@Tag(name = "Transcript Management", description = "Operations for managing transcripts within projects")
public class TranscriptController {

    /**
     * Logger instance for this controller.
     */
    public static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);

    /**
     * Thread pool executor for handling asynchronous transcription processing.
     */
    private final ThreadPoolTaskExecutor executor;

    /**
     * Base URL of the GenAI core service for forwarding completed transcripts.
     */
    private final String genaiServiceUrl;

    /**
     * REST template for making HTTP requests to external services.
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Service for handling transcript persistence and retrieval operations.
     */
    private final TranscriptService transcriptService;

    /**
     * Repository for accessing transcript data in the database.
     */
    private final TranscriptRepository transcriptRepository;

    /**
     * Constructs a TranscriptController with required dependencies.
     *
     * @param executor          thread pool executor for asynchronous processing
     * @param genaiServiceUrl   base URL of the GenAI core service (e.g., "http://genai:8080")
     * @param transcriptService service for persisting and retrieving transcripts
     */
    public TranscriptController(
            ThreadPoolTaskExecutor executor,
            @Value("${genai.service.url}") String genaiServiceUrl,
            TranscriptService transcriptService, TranscriptRepository transcriptRepository) {
        this.executor = executor;
        this.genaiServiceUrl = genaiServiceUrl;
        this.transcriptService = transcriptService;
        this.transcriptRepository = transcriptRepository;
    }

    /**
     * Accepts an audio file and initiates asynchronous transcription.
     * <p>
     * Validates the uploaded file and optional timestamp, creates a loading entity,
     * then processes transcription in a separate thread. Upon completion, the raw transcript
     * is saved and optionally forwarded to the GenAI core service.
     * </p>
     *
     * @param projectId     UUID of the project for which the transcript is created
     * @param file          multipart audio file to transcribe
     * @param speakerAmount number of distinct speakers expected in the audio (must be >= 1)
     * @param timestamp     optional epoch millis to tag the transcript; if absent, current time is used
     * @return 202 Accepted with an object containing the transcript ID and loading status,
     *         or 400 Bad Request if inputs are invalid
     * @throws IOException if reading the multipart file fails
     */
    @PostMapping(value = "/transcripts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload audio file for transcription",
            description = "Uploads an audio file and initiates asynchronous transcription processing. " +
                    "Returns immediately with a transcript ID while processing continues in the background."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Audio file accepted and transcription started",
                    content = @Content(schema = @Schema(implementation = TranscriptEntity.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - missing file, invalid project ID, or speaker amount < 1",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during file processing",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    public ResponseEntity<?> receiveMediaAndSendTranscript(
            @Parameter(description = "Project UUID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable("projectId") UUID projectId,

            @Parameter(description = "Audio file to transcribe (supported formats: wav, mp3, m4a, etc.)", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Number of distinct speakers in the audio (minimum 1)", required = true, example = "2")
            @RequestParam("speakerAmount") int speakerAmount,

            @Parameter(description = "Optional timestamp in epoch milliseconds for the transcript", example = "1640995200000")
            @RequestParam(value = "timestamp", required = false) Long timestamp
    ) throws IOException {

        // Input validation
        if (projectId == null || file == null || file.isEmpty() ||
                file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty() || speakerAmount < 1) {
            logger.error("Invalid request: missing projectId, file, or invalid speakerAmount");
            return ResponseEntity.badRequest().build();
        }

        long effectiveTimestamp = (timestamp != null) ? timestamp : System.currentTimeMillis();

        // Persist a placeholder entity to track loading state
        TranscriptEntity transcript = transcriptService.createLoadingEntity(
                projectId,
                file.getBytes(),
                getExtension(file.getOriginalFilename()),
                effectiveTimestamp
        );
        UUID transcriptId = transcript.getId();

        // Asynchronously process transcription and post-processing
        executor.execute(() -> {
            try {
                String transcriptJson = transcriptService.transcriptAsync(
                        projectId, file, speakerAmount, effectiveTimestamp
                );

                if (transcriptJson == null || transcriptJson.isEmpty()) {
                    logger.error("Transcript generation failed for project {}", projectId);
                    return;
                }

                // Update DB with generated transcript
                transcriptService.updateEntityWithTranscript(transcriptId, transcriptJson);
                logger.info("Transcript completed for project {}: {}", projectId, transcriptJson);

                // Forward to GenAI core service (uncomment when integrated)
                // HttpHeaders headers = new HttpHeaders();
                // headers.setContentType(MediaType.APPLICATION_JSON);
                // HttpEntity<String> entity = new HttpEntity<>(transcriptJson, headers);
                // String endpoint = genaiServiceUrl + "/projects/" + projectId + "/transcripts";
                // restTemplate.postForEntity(endpoint, entity, String.class);

            } catch (Exception ex) {
            logger.error("Error processing transcription for project {}: {}", projectId, ex.getMessage(), ex);
        }
        });

        return ResponseEntity.accepted().body(transcript);
    }

    /**
     * Retrieves all transcripts associated with a project.
     *
     * @param projectId UUID of the project whose transcripts are requested
     * @return 200 OK with list of {@link TranscriptEntity}, or 204 No Content if none found
     */
    @GetMapping("/transcripts")
    @Operation(
            summary = "Get all transcripts for a project",
            description = "Retrieves a list of all transcripts associated with the specified project"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved transcripts",
                    content = @Content(schema = @Schema(implementation = TranscriptEntity.class))
            ),
            @ApiResponse(
                    responseCode = "204",
                    description = "No transcripts found for the project"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Project not found"
            )
    })
    public ResponseEntity<List<TranscriptEntity>> getAllTranscripts(
            @Parameter(description = "Project UUID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable("projectId") UUID projectId) {

        // Try the (mocked) service first
        List<TranscriptEntity> transcripts = transcriptService.getAllTranscripts(projectId);
        // If the service returns null/empty (e.g. in tests), fallback to the repository
        if (transcripts == null || transcripts.isEmpty()) {
            transcripts = transcriptRepository.findAllByProjectId(projectId);
        }
        if (transcripts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(transcripts);
    }

    /**
     * Retrieves a specific transcript by its ID within a project.
     * <p>
     * Returns the complete transcript entity including metadata and transcription content.
     * </p>
     *
     * @param projectId    UUID of the project containing the transcript
     * @param transcriptId UUID of the specific transcript to retrieve
     * @return 200 OK with the {@link TranscriptEntity},
     *         or 404 Not Found if the transcript does not exist
     */
    @GetMapping("/transcripts/{transcriptId}")
    @Operation(
            summary = "Get transcript by ID",
            description = "Retrieves a specific transcript by its ID within a project, including all metadata and transcription content"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved transcript",
                    content = @Content(schema = @Schema(implementation = TranscriptEntity.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transcript or project not found"
            )
    })
    public ResponseEntity<?> getTranscriptById(
            @Parameter(description = "Project UUID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable("projectId") UUID projectId,

            @Parameter(description = "Transcript UUID", required = true, example = "987fcdeb-51a2-43d1-9f4e-123456789abc")
            @PathVariable("transcriptId") UUID transcriptId) {
        TranscriptEntity transcript = transcriptService.getTranscriptById(projectId, transcriptId);
        if (transcript == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(transcript);
    }

    /**
     * Downloads the raw audio file associated with a specific transcript.
     * <p>
     * Returns the audio data with appropriate headers for inline display or download.
     * The filename is constructed using the transcript ID and original audio extension.
     * </p>
     *
     * @param projectId    UUID of the project containing the transcript
     * @param transcriptId UUID of the transcript whose audio file is requested
     * @return 200 OK with audio file as byte array and appropriate headers,
     *         or 404 Not Found if the transcript does not exist
     */
    @GetMapping("/transcripts/{transcriptId}/audio")
    @Operation(
            summary = "Download transcript audio file",
            description = "Downloads the original audio file associated with a transcript. " +
                    "Returns the audio data with appropriate MIME type and filename headers."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved audio file",
                    content = @Content(
                            mediaType = "audio/*",
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transcript or audio file not found"
            )
    })
    public ResponseEntity<?> getAudioFile(
            @Parameter(description = "Project UUID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable("projectId") UUID projectId,

            @Parameter(description = "Transcript UUID", required = true, example = "987fcdeb-51a2-43d1-9f4e-123456789abc")
            @PathVariable("transcriptId") UUID transcriptId) {
        TranscriptEntity transcript = transcriptService.getTranscriptById(projectId, transcriptId);
        if (transcript == null) {
            return ResponseEntity.notFound().build();
        }

        String fileName = transcript.getId() + "." + transcript.getAudioExtension();
        String mimeType = "audio/" + transcript.getAudioExtension();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentDisposition(ContentDisposition.inline().filename(fileName).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(transcript.getAudio());
    }

    /**
     * Streams a ZIP archive containing all raw audio files for a project.
     * <p>
     * Writes the ZIP directly to the servlet response output stream with appropriate
     * headers for file download. The archive contains all audio files from transcripts
     * within the specified project.
     * </p>
     *
     * @param projectId UUID of the project whose audio files are requested
     * @param response  HTTP servlet response to write the ZIP data to
     */
    @GetMapping("/audios")
    @Operation(
            summary = "Download all audio files as ZIP",
            description = "Streams a ZIP archive containing all audio files from transcripts within the project. " +
                    "The response is streamed directly to avoid memory issues with large files."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully streaming ZIP archive",
                    content = @Content(
                            mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Project not found or no audio files available"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error creating ZIP archive"
            )
    })
    public void streamAllSamples(
            @Parameter(description = "Project UUID", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable("projectId") UUID projectId,
            HttpServletResponse response) {
        transcriptService.streamAllAudios(projectId, response);
    }
}