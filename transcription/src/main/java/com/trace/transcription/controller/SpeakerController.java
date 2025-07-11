package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.service.SpeakerService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing speaker profiles and audio samples within a transcription project.
 * <p>
 * Exposes endpoints to list, add, update, delete speakers and to stream all speaker samples as a ZIP archive.
 * </p>
 */
@RestController
@RequestMapping("projects/{projectId}")
public class SpeakerController {

    private static final Logger logger = LoggerFactory.getLogger(SpeakerController.class);

    private final SpeakerService speakerService;

    /**
     * Constructs a new SpeakerController with the provided service.
     *
     * @param speakerService service handling speaker persistence and operations
     */
    public SpeakerController(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    /**
     * Retrieves all speakers for the specified project.
     *
     * @param projectId UUID of the project
     * @return 200 OK with a list of {@link SpeakerEntity}, or 204 No Content if none are found
     */
    @GetMapping("/speakers")
    public ResponseEntity<List<SpeakerEntity>> getAllSpeakers(
            @PathVariable("projectId") UUID projectId) {
        List<SpeakerEntity> speakers = speakerService.getSpeakersByProjectId(projectId);
        if (speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(speakers);
    }

    /**
     * Adds multiple speakers to the project.
     * <p>
     * Expects parallel lists of user IDs, user names, and audio samples. All lists must be the same length.
     * </p>
     *
     * @param projectId       UUID of the project
     * @param userIds         list of unique speaker identifiers
     * @param userNames       list of display names matching the order of {@code userIds}
     * @param speakingSamples list of audio files corresponding to each speaker ID
     * @return 200 OK with a summary string if successful,
     *         400 Bad Request if list sizes mismatch,
     *         or 500 Internal Server Error on processing failure
     */
    @PostMapping("/speakers")
    public ResponseEntity<String> saveSpeakers(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("userIds") List<String> userIds,
            @RequestParam("userNames") List<String> userNames,
            @RequestParam("speakingSamples") List<MultipartFile> speakingSamples) {

        int count = userIds.size();
        if (userNames.size() != count || speakingSamples.size() != count) {
            String error = "Error: userIds, userNames, and speakingSamples must have the same number of elements.";
            logger.warn("Validation failed when saving speakers for project {}: {}", projectId, error);
            return ResponseEntity.badRequest().body(error);
        }

        String result = speakerService.saveSpeakers(projectId, userIds, userNames, speakingSamples);
        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            logger.error("Failed to save speakers for project {}", projectId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving speakers. Please check logs for details.");
        }
    }

    /**
     * Deletes a specific speaker from the project.
     *
     * @param projectId UUID of the project
     * @param userId    identifier of the speaker to delete
     * @return 200 OK if deleted,
     *         or 404 Not Found if the speaker does not exist
     */
    @DeleteMapping("/speakers/{userId}")
    public ResponseEntity<String> deleteSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId) {

        boolean deleted = speakerService.deleteSpeaker(projectId, userId);
        if (deleted) {
            return ResponseEntity.ok("Speaker " + userId + " deleted successfully.");
        } else {
            String msg = "Speaker with ID " + userId + " not found in project " + projectId + ".";
            logger.warn(msg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg);
        }
    }

    /**
     * Updates an existing speaker's name and/or audio sample.
     * <p>
     * Either or both parameters {@code userName} and {@code speakingSample} may be provided.
     * </p>
     *
     * @param projectId      UUID of the project
     * @param userId         identifier of the speaker to update
     * @param userName       (optional) new display name
     * @param speakingSample (optional) new audio file
     * @return 200 OK if update succeeds,
     *         or 404 Not Found if the speaker does not exist
     * @throws IOException if sample file processing fails
     */
    @PutMapping("/speakers/{userId}")
    public ResponseEntity<String> updateSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("userId") String userId,
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "speakingSample", required = false) MultipartFile speakingSample)
            throws IOException {

        boolean updated = speakerService.updateSpeaker(projectId, userId, userName, speakingSample);
        if (updated) {
            return ResponseEntity.ok("Speaker " + userId + " updated successfully.");
        } else {
            String msg = "Speaker with ID " + userId + " not found in project " + projectId + ".";
            logger.warn(msg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(msg);
        }
    }

    /**
     * Streams a ZIP archive of all speaker audio samples for the given project.
     * <p>
     * Writes the ZIP directly to the response output stream.
     * </p>
     *
     * @param projectId UUID of the project
     * @param response  servlet response to write the ZIP data
     */
    @GetMapping("/samples")
    public void streamAllSamples(
            @PathVariable("projectId") UUID projectId,
            HttpServletResponse response) {
        speakerService.streamAllSamples(projectId, response);
    }
}