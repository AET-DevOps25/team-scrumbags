package com.trace.transcription.controller;

import com.trace.transcription.dto.LoadingResponse;
import com.trace.transcription.service.TranscriptService;
import com.trace.transcription.model.TranscriptEntity;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.apache.commons.io.FilenameUtils.getExtension;

/**
 * REST controller responsible for handling transcript-related operations within a transcription project.
 * <p>
 * Provides endpoints to upload audio for transcription, list existing transcripts,
 * and stream raw audio files as ZIP archives.
 * </p>
 */
@RestController
@RequestMapping("projects/{projectId}")
public class TranscriptController {

    public static Logger logger = LoggerFactory.getLogger(TranscriptController.class);

    private final ThreadPoolTaskExecutor executor;
    private final String genaiServiceUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final TranscriptService transcriptService;

    /**
     * Constructs a TranscriptController with required dependencies.
     *
     * @param executor         thread pool executor for asynchronous processing
     * @param genaiServiceUrl  base URL of the GenAI core service (e.g., "http://genai:8080")
     * @param transcriptService service for persisting and retrieving transcripts
     */
    public TranscriptController(
            ThreadPoolTaskExecutor executor,
            @Value("${genai.service.url}") String genaiServiceUrl,
            TranscriptService transcriptService) {
        this.executor = executor;
        this.genaiServiceUrl = genaiServiceUrl;
        this.transcriptService = transcriptService;
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
     * @return 202 Accepted with a {@link LoadingResponse} containing the transcript ID and loading status,
     *         or 400 Bad Request if inputs are invalid
     * @throws IOException if reading the multipart file fails
     */
    @PostMapping("/transcripts")
    public ResponseEntity<LoadingResponse> receiveMediaAndSendTranscript(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("speakerAmount") int speakerAmount,
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
                logger.error("Error processing transcription for project {}: {}", projectId, ex.getMessage());
            }
        });

        // Return loading response immediately
        LoadingResponse response = new LoadingResponse(transcriptId, true);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Retrieves all transcripts associated with a project.
     *
     * @param projectId UUID of the project whose transcripts are requested
     * @return 200 OK with list of {@link TranscriptEntity}, or 204 No Content if none found
     */
    @GetMapping("/transcripts")
    public ResponseEntity<List<TranscriptEntity>> getAllTranscripts(
            @PathVariable("projectId") UUID projectId) {

        List<TranscriptEntity> transcripts = transcriptService.getAllTranscripts(projectId);
        if (transcripts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(transcripts);
    }

    /**
     * Streams a ZIP archive containing all raw audio files for a project.
     * <p>
     * Writes the ZIP directly to the servlet response output stream.
     * </p>
     *
     * @param projectId UUID of the project whose audio files are requested
     * @param response  HTTP response used to stream the ZIP data
     */
    @GetMapping("/audios")
    public void streamAllSamples(
            @PathVariable("projectId") UUID projectId,
            HttpServletResponse response
    ) {
        transcriptService.streamAllAudios(projectId, response);
    }
}