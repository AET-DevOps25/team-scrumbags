package com.trace.transcription.service;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.trace.transcription.controller.SpeakerController.logger;

@Service
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

    //get all speaking samples for project and return all files as zip
    public void streamAllSamples(UUID projectId, HttpServletResponse response) {
        List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
        if (speakers.isEmpty()) {
            logger.warn("No speakers found for project {}", projectId);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 2. Prepare response headers for a ZIP download:
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"media-files.zip\""
        );

        // 3. Stream the ZIP directly to the response output stream:
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            byte[] buffer = new byte[8192];

            for (SpeakerEntity m : speakers) {
                // Build a predictable, ordered filename:
                String filename = m.getId() + "." + m.getSampleExtension();

                // Add a new ZIP entry
                zos.putNextEntry(new ZipEntry(filename));

                // Write the byte[] LOB in chunks
                try (ByteArrayInputStream in = new ByteArrayInputStream(m.getSpeakingSample())) {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }

                zos.closeEntry();
            }

            // Finish writing the ZIP (optional: zos.finish() is called by close())
            zos.finish();
        } catch (IOException e) {
            logger.error("Error creating ZIP file for project {}: {}", projectId, e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
