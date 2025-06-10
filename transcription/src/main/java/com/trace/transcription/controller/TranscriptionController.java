package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import com.trace.transcription.repository.TranscriptRepository;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;
import jep.JepException;
import jep.SharedInterpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;


@RestController
public class TranscriptionController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);
    private final ThreadPoolTaskExecutor executor;

    private final String coreServiceUrl; // e.g. "http://core-service:8080"
    private final RestTemplate restTemplate = new RestTemplate();

    private final SpeakerRepository speakerRepository;
    private final TranscriptRepository transcriptRepository;

    public TranscriptionController(ThreadPoolTaskExecutor executor, @Value("${core.service.url}") String coreServiceUrl, SpeakerRepository speakerRepository, TranscriptRepository transcriptRepository) {
        this.executor = executor;
        this.coreServiceUrl = coreServiceUrl;
        this.speakerRepository = speakerRepository;
        this.transcriptRepository = transcriptRepository;
    }

    @GetMapping("/test")
    public ResponseEntity<?> getEntity() {
        return ResponseEntity.ok("Transcription Service is running");
    }

    @GetMapping("projects/{projectId}/all-speakers")
    public ResponseEntity<List<SpeakerEntity>> getAllSpeakers(@PathVariable("projectId") UUID projectId) {
        List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
        if (speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(speakers);
    }

    @GetMapping("/{id}/test")
    public ResponseEntity<?> getEntityById(@PathVariable("id") String id) {
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
    @PostMapping("projects/{projectId}/speakers")
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
                speaker.setId(speakerId);
                speaker.setName(speakerName);
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
     * POST projects/{projectId}/receive
     * <p>
     * Request (multipart/form-data):
     *   - file: MultipartFile
     * <p>
     * Receives an audio file, processes it to generate a transcript, and sends the transcript to the core service.
     */
    @PostMapping("projects/{projectId}/receive")
    public DeferredResult<ResponseEntity<String>> receiveMediaAndSendTranscript(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "timestamp", required = false) Date timestamp
    ) {

        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>();

        executor.execute(() -> {
            try {
                String transcriptJson = transcriptAsync(projectId, file);

                // Forward JSON to core service
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(transcriptJson, headers);
                String endpoint = coreServiceUrl + "projects/" + projectId + "/transcripts";
                ResponseEntity<String> coreResponse = restTemplate.postForEntity(endpoint, entity, String.class);

                if (!coreResponse.getStatusCode().is2xxSuccessful()) {
                    logger.error("Core service returned {} when posting transcript: {}",
                            coreResponse.getStatusCode(), coreResponse.getBody());
                    deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to send transcript: " + coreResponse.getBody()));
                } else {
                    deferredResult.setResult(ResponseEntity.ok("Transcript successfully created and sent."));
                }
            } catch (Exception ex) {
                logger.error("Error processing audio for project {}: {}", projectId, ex.getMessage());
                deferredResult.setErrorResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Processing error: " + ex.getMessage()));
            }
        });

        return deferredResult;
    }

    private String transcriptAsync(UUID projectId, MultipartFile file) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("media-" + projectId + "-");
        try {
            // Save incoming file
            String cleanFileName = Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
                    .getFileName().toString();
            Path incomingPath = tempDir.resolve(cleanFileName);
            Files.copy(file.getInputStream(), incomingPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Stored incoming file: {} for project {}", cleanFileName, projectId);

            // Dump speaker samples
            List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
            for (SpeakerEntity speaker : speakers) {
                byte[] audio = speaker.getSpeakingSample();
                String ext = speaker.getSampleExtension();
                if (audio != null && audio.length > 0) {
                    Path spath = tempDir.resolve("sample-" + speaker.getId() + "." + ext);
                    Files.write(spath, audio, StandardOpenOption.CREATE);
                }
            }

            // Launch Python process
            String pythonPath = Paths.get(".venv", "bin", "python").toAbsolutePath().toString();
            ProcessBuilder pb = new ProcessBuilder("/opt/venv/bin/python", "transcriber.py", tempDir.toString())
                    .redirectErrorStream(true);

            Process proc = pb.start();

            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python exited with code " + exitCode + ": " + output);
            }
            String transcriptJson = output.toString();
            if (transcriptJson.isEmpty()) {
                throw new RuntimeException("Empty transcript from Python script");
            }
            return transcriptJson;
        } finally {
            // Cleanup
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            logger.info("Cleaned up temp directory: {}", tempDir);
        }
    }


    /**
     * DELETE /{projectId}/speakers/{speakerId}
     * <p>
     * Deletes the speaker with the given ID from the project.
     */
    @DeleteMapping("projects/{projectId}/speakers/{speakerId}")
    public ResponseEntity<String> deleteSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("speakerId") String speakerId) {

        // Check if speaker exists
        SpeakerEntity speaker = speakerRepository.findByProjectIdAndId(projectId, speakerId);
        if (speaker == null) {
            return ResponseEntity.notFound().build();
        }

        // Delete the speaker
        speakerRepository.delete(speaker);
        return ResponseEntity.ok("Speaker " + speakerId + " deleted successfully.");
    }


    /**
     * PUT /{projectId}/speakers/{speakerId}
     * <p>
     * Request (multipart/form-data):
     *   - speakerName: String (optional)
     *   - speakingSample: MultipartFile (optional)
     * <p>
     * Updates the speaker's name and/or speaking sample.
     */
    @PutMapping("projects/{projectId}/speakers/{speakerId}")
    public ResponseEntity<String> modifySpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("speakerId") String speakerId,
            @RequestParam(value = "speakerName", required = false) String speakerName,
            @RequestParam(value = "speakingSample", required = false) MultipartFile speakingSample) {

        SpeakerEntity speaker = speakerRepository.findByProjectIdAndId(projectId, speakerId);
        if (speaker == null) {
            return ResponseEntity.notFound().build();
        }

        if (speakerName != null && !speakerName.isEmpty()) {
            speaker.setName(speakerName);
        }
        if (speakingSample != null && !speakingSample.isEmpty()) {
            try {
                String extension = FilenameUtils.getExtension(speakingSample.getOriginalFilename());
                speaker.setSpeakingSample(speakingSample.getBytes());
                speaker.setSampleExtension(extension);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error saving speaking sample: " + e.getMessage());
            }
        }

        speakerRepository.save(speaker);
        return ResponseEntity.ok("Speaker " + speakerId + " updated successfully.");
    }

    /**
     * GET /{projectId}/samples
     * <p>
     * Returns a list of all samples (speaker IDs with their sample extensions) for the given project.
     */
    @GetMapping("projects/{projectId}/samples")
    public ResponseEntity<List<String>> getAllRecordings(
            @PathVariable("projectId") UUID projectId) {

        List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);

        if (speakers == null || speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        List<String> recordings = new ArrayList<>();
        for (SpeakerEntity speaker : speakers) {
            if (speaker.getSpeakingSample() != null && speaker.getSpeakingSample().length > 0) {
                recordings.add(speaker.getId() + "." + speaker.getSampleExtension());
            }
        }
        if (recordings.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(recordings);
    }


    /**
     * GET /{projectId}/transcripts
     * <p>
     * Returns all transcripts for the given project.
     */
    @GetMapping("projects/{projectId}/transcripts")
    public ResponseEntity<List<String>> getAllTranscripts(@PathVariable("projectId") UUID projectId) {
        List<String> transcripts = transcriptRepository.findAllByProjectId(projectId);
        if (transcripts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(transcripts);
    }


    //include timestamp in transcript, extract from audio file metadata if available, else request timestamp / current time
    //launch processing in thread to not block
    //todo add transcripts to db with timestamp

}