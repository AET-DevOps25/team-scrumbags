package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@RestController
public class TranscriptionController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);
    private final Path uploadDir;
    private final SpeakerRepository speakerRepository;

    public TranscriptionController(@Value("${file.local-dir}") String uploadDir, SpeakerRepository speakerRepository) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.speakerRepository = speakerRepository;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> getEntity() {
        return ResponseEntity.ok("Transcription Service is running");
    }

    /**
     * POST /{projectId}/receive
     *
     * Request (multipart/form-data):
     *   - file: MultipartFile
     *
     * Stores the uploaded file in a project-specific directory.
     */
    @PostMapping("/{projectId}/receive")
    public ResponseEntity<String> receiveFile(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("file") MultipartFile file) {
        try {
            // create project directory if it doesn't exist
            Path projectDir = uploadDir.resolve(projectId.toString());
            Files.createDirectories(projectDir);

            // clean filename
            String cleanFileName = Paths.get(Objects.requireNonNull(file
                                        .getOriginalFilename()))
                                        .getFileName()
                                        .toString();

            Path targetLocation = projectDir.resolve(cleanFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("Received and stored: " + cleanFileName + " under project: " + projectId);
        } catch (IOException ex) {
            logger.error("Failed to store file for project {}: {}", projectId, ex.getMessage());
            return ResponseEntity
                    .status(500)
                    .body("Failed to store file for project " + projectId + ": " + ex.getMessage());
        }
    }

    /**
     * POST /{projectId}/speakers
     *
     * Request (multipart/form-data):
     *   - speakerIds        : List of String      (e.g. ["id1","id2",...])
     *   - speakerNames      : List of String      (must match length of speakerIds)
     *   - speakingSamples   : List of MultipartFile (must match length of speakerIds)
     *
     * All lists must be the same size. Each index i corresponds to one Speaker record.
     */
    @PostMapping("/{projectId}/speakers")
    public ResponseEntity<String> uploadSpeakers(
            @PathVariable("projectId") String projectId,
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

                // create and set entity
                SpeakerEntity speaker = new SpeakerEntity();
                speaker.setSpeakerId(speakerId);
                speaker.setSpeakerName(speakerName);
                speaker.setProjectId(projectId);
                speaker.setSpeakingSample(file.getBytes());

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

}