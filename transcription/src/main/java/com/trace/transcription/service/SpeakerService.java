package com.trace.transcription.service;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.trace.transcription.controller.SpeakerController.logger;

public class SpeakerService {

    private final SpeakerRepository speakerRepository;

    public SpeakerService(SpeakerRepository speakerRepository) {
        this.speakerRepository = speakerRepository;
    }

    public List<SpeakerEntity> getSpeakersByProjectId(UUID projectId) {
        return speakerRepository.findAllByProjectId(projectId);
    }

    public boolean saveSpeakers(
            UUID projectId,
            List<String> speakerIds,
            List<String> speakerNames,
            List<MultipartFile> speakingSamples) {
        int count = speakerIds.size();

        List<SpeakerEntity> toSave = new ArrayList<>(count);

        try {
            for (int i = 0; i < count; i++) {
                String speakerId   = speakerIds.get(i);
                String speakerName = speakerNames.get(i);
                MultipartFile file = speakingSamples.get(i);
                String extension = FilenameUtils.getExtension(file.getOriginalFilename());

                // create and set entity
                SpeakerEntity speaker = new SpeakerEntity(speakerId, speakerName, projectId, file.getBytes(), extension);

                toSave.add(speaker);
            }

            // save speakers
            speakerRepository.saveAll(toSave);
            logger.info("Saved {} speakers", count);
        } catch (Exception e) {
            logger.error("Error saving speakers: {}", e.getMessage(), e);
            return false;
        }
        return true;
    }

    public SpeakerEntity getSpeakerById(UUID projectId, String speakerId) {
        return speakerRepository.findByProjectIdAndId(projectId, speakerId);
    }

    public boolean deleteSpeaker(UUID projectId, String speakerId) {
        SpeakerEntity speaker = getSpeakerById(projectId, speakerId);
        if (speaker != null) {
            speakerRepository.delete(speaker);
            logger.info("Deleted speaker with ID: {}", speakerId);
            return true;
        } else {
            logger.warn("Speaker with ID {} not found in project {}", speakerId, projectId);
            return false;
        }
    }

    public boolean updateSpeaker(
            UUID projectId,
            String speakerId,
            String speakerName,
            MultipartFile speakingSample) throws IOException {
        SpeakerEntity speaker = getSpeakerById(projectId, speakerId);
        if (speaker == null) {
            logger.warn("Speaker with ID {} not found in project {}", speakerId, projectId);
            return false;
        }

        if (speakerName != null && !speakerName.isEmpty()) {
            speaker.setName(speakerName);
        }
        if (speakingSample != null && !speakingSample.isEmpty()) {
            String extension = FilenameUtils.getExtension(speakingSample.getOriginalFilename());
            speaker.setSpeakingSample(speakingSample.getBytes());
            speaker.setSampleExtension(extension);
        }

        speakerRepository.save(speaker);
        logger.info("Updated speaker with ID: {}", speakerId);
        return true;
    }

    public List<String> getAllSamples(UUID projectId) {
        List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
        List<String> recordings = new ArrayList<>(speakers.size());

        for (SpeakerEntity speaker : speakers) {
            String recording = speaker.getId() + "." + speaker.getSampleExtension();
            recordings.add(recording);
        }

        return recordings;
    }
}
