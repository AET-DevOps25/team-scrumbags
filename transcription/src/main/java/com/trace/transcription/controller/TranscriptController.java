package com.trace.transcription.controller;

import com.trace.transcription.service.TranscriptService;
import com.trace.transcription.model.TranscriptEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;


@RestController
public class TranscriptController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptController.class);

    private final ThreadPoolTaskExecutor executor;

    private final String genaiServiceUrl; // e.g. "http://genai:8080"
    private final RestTemplate restTemplate = new RestTemplate();

    private final TranscriptService transcriptService;

    public TranscriptController(ThreadPoolTaskExecutor executor, @Value("${genai.service.url}") String genaiServiceUrl, TranscriptService transcriptService) {
        this.executor = executor;
        this.genaiServiceUrl = genaiServiceUrl;
        this.transcriptService = transcriptService;
    }

    /**
     * POST projects/{projectId}/receive
     * <p>
     * Request (multipart/form-data):
     *   - file: MultipartFile
     * <p>
     * Receives an audio file, processes it to generate a transcript, and sends the transcript to the core service.
     */
    @PostMapping("projects/{projectId}/transcripts")
    public DeferredResult<ResponseEntity<String>> receiveMediaAndSendTranscript(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("speakerAmount") int speakerAmount,
            @RequestParam(value = "timestamp", required = false) Long timestamp
    ) {

        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>(300_000L);

        // Validate inputs
        if (projectId == null || file == null || file.isEmpty()) {
            logger.error("Invalid request: projectId or file is missing");
            deferredResult.setErrorResult(ResponseEntity.badRequest().body("Project ID and file are required."));
            return deferredResult;
        }

        if (speakerAmount < 1) {
            logger.error("Invalid request: speakerAmount must be at least 1");
            deferredResult.setErrorResult(ResponseEntity.badRequest().body("Speaker amount must be at least 1."));
            return deferredResult;
        }

        if (timestamp == null) {
            timestamp = System.currentTimeMillis(); // Use current time if not provided
        }

        Long finalTimestamp = timestamp;
        executor.execute(() -> {
            try {

                String transcriptJson = transcriptService.transcriptAsync(projectId, file, speakerAmount, finalTimestamp);

                if (transcriptJson == null || transcriptJson.isEmpty()) {
                    logger.error("Transcript generation failed for project {}. See logs for details.", projectId);
                    deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Transcript generation failed. See logs for details."));
                    return;
                }

                // Save transcript to database
                transcriptService.saveFromJson(transcriptJson);

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
                deferredResult.setResult(ResponseEntity.ok(transcriptJson));
            } catch (Exception ex) {
                logger.error("Error processing audio for project {}: {}", projectId, ex.getMessage());
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Processing error: " + ex.getMessage()));
            }
        });

        return deferredResult;
    }

    /**
     * GET /{projectId}/transcripts
     * <p>
     * Returns all transcripts for the given project.
     */
    @GetMapping("projects/{projectId}/transcripts")
    public ResponseEntity<List<TranscriptEntity>> getAllTranscripts(@PathVariable("projectId") UUID projectId) {
        List<TranscriptEntity> transcripts = transcriptService.getAllTranscripts(projectId);
        if (transcripts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(transcripts);
    }

}