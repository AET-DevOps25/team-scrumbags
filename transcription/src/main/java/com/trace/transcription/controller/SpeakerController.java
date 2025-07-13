package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.service.SpeakerService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
public class SpeakerController {

    public static final Logger logger = LoggerFactory.getLogger(SpeakerController.class);

    private final SpeakerService speakerService;

    public SpeakerController(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    /**
     * GET /{projectId}/all-speakers
     * <p>
     * Returns a list of all speakers for the given project.
     */
    @GetMapping("projects/{projectId}/speakers")
    public ResponseEntity<List<SpeakerEntity>> getAllSpeakers(@PathVariable("projectId") UUID projectId) {
        List<SpeakerEntity> speakers = speakerService.getSpeakersByProjectId(projectId);
        if (speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(speakers);
    }

    /**
     * GET /{projectId}/speakers/{userId}/sample
     * <p>
     * Returns the audio sample for a specific speaker.
     */
    @GetMapping("projects/{projectId}/speakers/{userId}/sample")
    public ResponseEntity<byte[]> getSpeakerSample(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId) {
        SpeakerEntity speaker = speakerService.getSpeakerById(projectId, userId);
        if (speaker == null || speaker.getSpeakingSample() == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", speaker.getOriginalFileName());
        headers.setContentLength(speaker.getSpeakingSample().length);

        return new ResponseEntity<>(speaker.getSpeakingSample(), headers, HttpStatus.OK);
    }

    /**
     * POST /{projectId}/speakers
     * <p>
     * Request (multipart/form-data):
     *   - userIds        : List of String      (e.g. ["id1","id2",...])
     *   - userNames      : List of String      (must match length of userIds)
     *   - speakingSamples   : List of MultipartFile (must match length of userIds)
     * <p>
     * All lists must be the same size. Each index corresponds to one Speaker record.
     */
    @PostMapping("projects/{projectId}/speakers")
    public ResponseEntity<String> saveSpeakers(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("userIds") List<String> userIds,
            @RequestParam("userNames") List<String> userNames,
            @RequestParam("speakingSamples") List<MultipartFile> speakingSamples) {

        // list same size check
        int count = userIds.size();
        if (userNames.size() != count || speakingSamples.size() != count) {
            return ResponseEntity.badRequest()
                    .body("Error: userIds, userNames, and speakingSamples must have the same number of elements.");
        }

        String speakers = speakerService.saveSpeakers(projectId, userIds, userNames, speakingSamples);
        if (speakers != null) {
            return ResponseEntity.ok(speakers);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving speakers. Please check the logs for details.");
        }
    }

    /**
     * POST /{projectId}/speakers/{userId}
     * <p>
     * Request (multipart/form-data):
     *   - userName: String
     *   - speakingSample: MultipartFile
     * <p>
     * Creates a new speaker with the given details.
     */
    @PostMapping("projects/{projectId}/speakers/{userId}")
    public ResponseEntity<?> saveSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId,
            @RequestParam("userName") String userName,
            @RequestParam("speakingSample") MultipartFile speakingSample) {
        try {
            if (speakerService.getSpeakerById(projectId, userId) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Speaker with ID " + userId + " already exists in project " + projectId + ".");
            }
            SpeakerEntity savedSpeaker = speakerService.saveSpeaker(projectId, userId, userName, speakingSample);
            if (savedSpeaker != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(savedSpeaker);
            } else {
                return ResponseEntity.badRequest().body("Could not save speaker. The audio sample might be too short.");
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error saving speaker {} for project {}: {}", userId, projectId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving speaker: " + e.getMessage());
        }
    }


    /**
     * DELETE /
     * {projectId}/speakers/{userId}
     * <p>
     * Deletes the speaker with the given ID from the project.
     */
    @DeleteMapping("projects/{projectId}/speakers/{userId}")
    public ResponseEntity<String> deleteSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId) {

        if (speakerService.deleteSpeaker(projectId, userId)) {
            return ResponseEntity.ok("Speaker " + userId + " deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Speaker with ID " + userId + " not found in project " + projectId + ".");
        }
    }

    /**
     * PUT /{projectId}/speakers/{userId}
     * <p>
     * Request (multipart/form-data):
     *   - userName: String (optional)
     *   - speakingSample: MultipartFile (optional)
     * <p>
     * Updates the speaker's name and/or speaking sample.
     */
    @PutMapping("projects/{projectId}/speakers/{userId}")
    public ResponseEntity<?> updateSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId,
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "speakingSample", required = false) MultipartFile speakingSample) throws IOException {

        SpeakerEntity updatedSpeaker = speakerService.updateSpeaker(projectId, userId, userName, speakingSample);
        if (updatedSpeaker != null) {
            return ResponseEntity.ok(updatedSpeaker);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Speaker with ID " + userId + " not found in project " + projectId + ".");
        }
    }

    /**
     * GET /{projectId}/samples
     * <p>
     * Streams back a zip with all samples (speaker IDs with their sample extensions) for the given project.
     */
    @GetMapping("projects/{projectId}/samples")
    public void streamAllSamples(@PathVariable("projectId") UUID projectId, HttpServletResponse response) {
        speakerService.streamAllSamples(projectId, response);
    }
}