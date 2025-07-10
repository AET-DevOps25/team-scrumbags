package com.trace.transcription.controller;

import com.trace.transcription.repository.TranscriptRepository;
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
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.apache.commons.io.FilenameUtils.getExtension;


@RestController
public class TranscriptController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);

    private final ThreadPoolTaskExecutor executor;

    private final String genaiServiceUrl; // e.g. "http://genai:8080"
    private final RestTemplate restTemplate = new RestTemplate();

    private final TranscriptService transcriptService;
    private final TranscriptRepository transcriptRepository;

    public TranscriptController(ThreadPoolTaskExecutor executor, @Value("${genai.service.url}") String genaiServiceUrl, TranscriptService transcriptService, TranscriptRepository transcriptRepository) {
        this.executor = executor;
        this.genaiServiceUrl = genaiServiceUrl;
        this.transcriptService = transcriptService;
        this.transcriptRepository = transcriptRepository;
    }

    /**
     * POST projects/{projectId}/transcripts
     * <p>
     * Request (multipart/form-data):
     *   - file: MultipartFile
     * <p>
     * Receives an audio file, processes it to generate a transcript, and sends the transcript to the core service.
     */
    @PostMapping("projects/{projectId}/transcripts")
    public ResponseEntity<LoadingResponse> receiveMediaAndSendTranscript(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("speakerAmount") int speakerAmount,
            @RequestParam(value = "timestamp", required = false) Long timestamp
    ) throws IOException {

        // Validate inputs
        if (projectId == null || file == null || file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty() || speakerAmount < 1) {
            logger.error("Invalid request: projectId or file is missing");
            return ResponseEntity.badRequest().build();
        }

        if (timestamp == null) {
            timestamp = System.currentTimeMillis(); // Use current time if not provided
        }

        Long finalTimestamp = timestamp;

        // Persist loading entity
        TranscriptEntity transcript = transcriptService.createLoadingEntity(projectId, file.getBytes(), getExtension(file.getOriginalFilename()), timestamp);
        UUID transcriptId = transcript.getId();

        executor.execute(() -> {
            try {

                String transcriptJson = transcriptService.transcriptAsync(projectId, file, speakerAmount, finalTimestamp);

                if (transcriptJson == null || transcriptJson.isEmpty()) {
                    logger.error("Transcript generation failed for project {}. See logs for details.", projectId);
                    return;
                }

                // Save transcript to database
                transcriptService.updateEntityWithTranscript(transcriptId, transcriptJson);

                logger.info("Transcript successfully created for project {}: {}", projectId, transcriptJson);

                // Forward JSON to core service
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(transcriptJson, headers);
                // todo uncomment when merge services
                /*String endpoint = genaiServiceUrl + "projects/" + projectId + "/transcripts";
                ResponseEntity<String> coreResponse = restTemplate.postForEntity(endpoint, entity, String.class);

                if (!coreResponse.getStatusCode().is2xxSuccessful()) {
                    logger.error("Core service returned {} when posting transcript: {}",
                            coreResponse.getStatusCode(), coreResponse.getBody());
                    deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to send transcript: " + coreResponse.getBody()));
                } else {
                    deferredResult.setResult(ResponseEntity.ok("Transcript successfully created and sent."));
                }*/
                //todo remove this when genai service is ready
            } catch (Exception ex) {
                logger.error("Error processing audio for project {}: {}", projectId, ex.getMessage());
            }
        });

        // Return 202 Accepted with loading response
        LoadingResponse response = new LoadingResponse(transcriptId, true);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET projects/{projectId}/transcripts
     * <p>
     * Returns all transcripts for the given project.
     */
    @GetMapping("projects/{projectId}/transcripts")
    public ResponseEntity<List<TranscriptEntity>> getAllTranscripts(@PathVariable("projectId") UUID projectId) {
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
     * GET projects/{projectId}/audios
     * <p>
     * Streams back a zip with all audios (transcript IDs with their sample extensions) for the given project.
     */
    @GetMapping("projects/{projectId}/audios")
    public void streamAllSamples(@PathVariable("projectId") UUID projectId, HttpServletResponse response) {
        transcriptService.streamAllAudios(projectId, response);
    }

}