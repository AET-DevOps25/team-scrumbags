package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import jep.JepException;
import jep.SharedInterpreter;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@RestController
public class TranscriptionController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);

    private final String coreServiceUrl; // e.g. "http://core-service:8080"
    private final SpeakerRepository speakerRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public TranscriptionController(@Value("${core.service.url}") String coreServiceUrl, @Value("${file.upload-dir}") String uploadDir, SpeakerRepository speakerRepository) {
        this.coreServiceUrl = coreServiceUrl;
        this.speakerRepository = speakerRepository;
    }

    @GetMapping("/test")
    public ResponseEntity<?> getEntity() {
        return ResponseEntity.ok("Transcription Service is running");
    }

    @GetMapping("/{projectId}/all-speakers")
    public ResponseEntity<List<SpeakerEntity>> getAllSpeakers(@PathVariable("projectId") UUID projectId) {
        List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
        if (speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(speakers);
    }

    @GetMapping("/{id}/test")
    public ResponseEntity<?> getEntityById(@PathVariable("id") String id) {
        // This is just a placeholder for testing purposes
        return ResponseEntity.ok("Test entity with ID: " + id);
    }

    /**
     * POST /{projectId}/speakers
     * <p>
     * Request (multipart/form-data):
     *   - speakerIds        : List of String      (e.g. ["id1","id2",...])
     *   - speakerNames      : List of String      (must match length of speakerIds)
     *   - speakingSamples   : List of MultipartFile (must match length of speakerIds)
     * <p>
     * All lists must be the same size. Each index corresponds to one Speaker record.
     */
    @PostMapping("/{projectId}/speakers")
    public ResponseEntity<String> uploadSpeakers(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("speakerIds") List<String> speakerIds,
            @RequestParam("speakerNames") List<String> speakerNames,
            @RequestParam("speakingSamples") List<MultipartFile> speakingSamples) {

        // list same size check
        int count = speakerIds.size();
        if (speakerNames.size() != count || speakingSamples.size() != count) {
            return ResponseEntity.badRequest()
                    .body("Error: speakerIds, speakerNames, and speakingSamples must have the same number of elements.");
        }

        List<SpeakerEntity> toSave = new ArrayList<>(count);

        try {
            for (int i = 0; i < count; i++) {
                String speakerId   = speakerIds.get(i);
                String speakerName = speakerNames.get(i);
                MultipartFile file = speakingSamples.get(i);
                String extension = FilenameUtils.getExtension(file.getOriginalFilename());

                // create and set entity
                SpeakerEntity speaker = new SpeakerEntity();
                speaker.setSpeakerId(speakerId);
                speaker.setSpeakerName(speakerName);
                speaker.setProjectId(projectId);

                speaker.setSpeakingSample(file.getBytes());
                speaker.setSampleExtension(extension);

                toSave.add(speaker);
            }

            // save speakers
            speakerRepository.saveAll(toSave);

            return ResponseEntity.ok("Successfully saved " + count + " speakers for project " + projectId);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error saving speakers: " + e.getMessage());
        }
    }

    /**
     * POST /{projectId}/receive
     * <p>
     * Request (multipart/form-data):
     *   - file: MultipartFile
     * <p>
     * 1. stores uploaded file into temp folder
     * 2. copies all speaker audio blobs to created temp folder
     * 3. JEP calls transcriber python script, passes temp folder path
     * 4. Transcriber returns JSON-formatted transcript
     * 5. sends transcript to core service //todo change send to genai service instead, when genai service ready
     */
    @PostMapping("/{projectId}/receive")
    public ResponseEntity<String> receiveMediaAndSendTranscript(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file) {

        Path tempDir;

        try {
            tempDir = Files.createTempDirectory("media-" + projectId + "-");
        } catch (IOException e) {
            logger.error("Failed to create temp directory for project {}: {}", projectId, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not create temp directory: " + e.getMessage());
        }

        try {
            // save incoming file to tempDir
            String cleanFileName = Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
                    .getFileName()
                    .toString();
            Path incomingPath = tempDir.resolve(cleanFileName);
            Files.copy(file.getInputStream(), incomingPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Stored incoming file: {} for project {}", cleanFileName, projectId);

            // copy all speaker audio blobs to tempDir
            List<SpeakerEntity> speakerList = speakerRepository.findAllByProjectId(projectId);
            for (SpeakerEntity speaker : speakerList) {
                byte[] audioBytes = speaker.getSpeakingSample(); // BLOB column
                String sampleExt = speaker.getSampleExtension(); // e.g. "mp3", "wav"
                if (audioBytes != null && audioBytes.length > 0) {
                    // Give each file a unique filename, e.g. "<speakerId>.<sampleExt>"
                    Path speakerPath = tempDir.resolve(speaker.getSpeakerId() + sampleExt);
                    Files.write(speakerPath, audioBytes, StandardOpenOption.CREATE);
                }
            }

            // use JEP to call transcriber.py script
            String transcriptJson;
            try (SharedInterpreter interp = new SharedInterpreter()) {
                // Add the directory containing my_transcriber.py to sys.path
                interp.exec("import sys; print(sys.executable)");
                interp.exec("sys.path.append('scripts')"); // Make sure 'scripts/' is in PYTHONPATH

                // Bind the tempDir path into Python as a variable
                interp.set("input_dir", tempDir.toString());

                // Import and call the function. Replace package name if needed.
                interp.exec("import transcriber");
                Object result = interp.getValue("transcriber.process_audio(input_dir)");

                if (result == null) {
                    throw new JepException("Python returned null transcript");
                }
                transcriptJson = result.toString();
            } catch (JepException e) {
                logger.error("JEP error processing audio for project {}: {}", projectId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error processing audio: " + e.getMessage());
            }

            // Step 4: Forward the JSON to the core service
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> coreRequest = new HttpEntity<>(transcriptJson, jsonHeaders);

            String coreEndpoint = coreServiceUrl + "/" + projectId + "/transcripts";
            ResponseEntity<String> coreResponse = restTemplate.postForEntity(
                    coreEndpoint, coreRequest, String.class);

            if (!coreResponse.getStatusCode().is2xxSuccessful()) {
                logger.error("Core service returned status {} when posting transcript",
                        coreResponse.getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to send transcript to core service: " + coreResponse.getBody());
            }

            return ResponseEntity.ok("Transcript successfully created and sent to core service.");

        } catch (IOException | JepException ex) {
            logger.error("Error processing audio for project {}: {}", projectId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Processing error: " + ex.getMessage());
        } finally {
            // (Optional) Clean up tempDir on exit if you don’t need to keep files around.
            // You can leave tempDir if you want to inspect it later.
        }
    }

}