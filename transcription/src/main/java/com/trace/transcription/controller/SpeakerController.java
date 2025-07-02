package com.trace.transcription.controller;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.service.SpeakerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
    @GetMapping("projects/{projectId}/all-speakers")
    public ResponseEntity<List<SpeakerEntity>> getAllSpeakers(@PathVariable("projectId") UUID projectId) {
        List<SpeakerEntity> speakers = speakerService.getSpeakersByProjectId(projectId);
        if (speakers.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(speakers);
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
    public ResponseEntity<String> saveSpeakers(
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

        if (speakerService.saveSpeakers(projectId, speakerIds, speakerNames, speakingSamples)) {
            return ResponseEntity.ok("Speakers uploaded successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving speakers. Please check the logs for details.");
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

        if (speakerService.deleteSpeaker(projectId, speakerId)) {
            return ResponseEntity.ok("Speaker " + speakerId + " deleted successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Speaker with ID " + speakerId + " not found in project " + projectId + ".");
        }
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
    public ResponseEntity<String> updateSpeaker(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("speakerId") String speakerId,
            @RequestParam(value = "speakerName", required = false) String speakerName,
            @RequestParam(value = "speakingSample", required = false) MultipartFile speakingSample) throws IOException {

        if (speakerService.updateSpeaker(projectId, speakerId, speakerName, speakingSample)) {
            return ResponseEntity.ok("Speaker " + speakerId + " updated successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Speaker with ID " + speakerId + " not found in project " + projectId + ".");
        }
    }

    /**
     * GET /{projectId}/samples
     * <p>
     * Returns a list of all samples (speaker IDs with their sample extensions) for the given project.
     */
    @GetMapping("projects/{projectId}/samples")
    public ResponseEntity<List<String>> getAllSamples(
            @PathVariable("projectId") UUID projectId) {

        return ResponseEntity.ok(speakerService.getAllSamples(projectId));
    }




}
