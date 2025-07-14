package com.trace.transcription.service;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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

    public String saveSpeakers(
            UUID projectId,
            List<String> userIds,
            List<String> userNames,
            List<MultipartFile> speakingSamples) {
        int count = userIds.size();

        List<SpeakerEntity> toSave = new ArrayList<>(count);

        try {
            for (int i = 0; i < count; i++) {
                String userId   = userIds.get(i);
                String userName = userNames.get(i);
                MultipartFile file = speakingSamples.get(i);

                File tmp = File.createTempFile("durationcheck-", file.getOriginalFilename());

                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                Duration d = getDurationWithFFprobe(tmp);

                //check if duration is more than 15 seconds
                if (d.isZero() || d.toMillis() < 15000) {
                    logger.warn("Speaker {} has zero or too short duration ({}), skipping", userId, d);
                    boolean deleted = tmp.delete();
                    if (!deleted) {
                        logger.warn("Temporary file {} could not be deleted", tmp.getAbsolutePath());
                    }
                    return null;
                }

                boolean deleted = tmp.delete();

                if (!deleted) {
                    logger.warn("Temporary file {} could not be deleted", tmp.getAbsolutePath());
                }

                String extension = FilenameUtils.getExtension(file.getOriginalFilename());

                // create and set entity
                SpeakerEntity speaker = new SpeakerEntity(userId, userName, projectId, file.getBytes(), extension, file.getOriginalFilename());

                toSave.add(speaker);
            }

            // save speakers
            speakerRepository.saveAll(toSave);
            logger.info("Saved {} speakers", count);
        } catch (Exception e) {
            logger.error("Error saving speakers: {}", e.getMessage(), e);
            return null;
        }
        // Return saved speakers in json format with id and name
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < toSave.size(); i++) {
            SpeakerEntity speaker = toSave.get(i);
            sb.append("{\"userId\":\"").append(speaker.getUserId()).append("\",\"userName\":\"").append(speaker.getUserName()).append("\"}");
            if (i < toSave.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public SpeakerEntity saveSpeaker(UUID projectId, String userId, String userName, MultipartFile speakingSample) throws IOException, InterruptedException {
        File tmp = File.createTempFile("durationcheck-", speakingSample.getOriginalFilename());

        try {
            try (InputStream in = speakingSample.getInputStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Duration d = getDurationWithFFprobe(tmp);

            //check if duration is more than 15 seconds
            if (d.isZero() || d.toMillis() < 15000) {
                logger.warn("Speaker {} has zero or too short duration ({}), skipping", userId, d);
                return null;
            }

            String extension = FilenameUtils.getExtension(speakingSample.getOriginalFilename());

            // create and set entity
            SpeakerEntity speaker = new SpeakerEntity(userId, userName, projectId, speakingSample.getBytes(), extension, speakingSample.getOriginalFilename());

            return speakerRepository.save(speaker);
        } finally {
            boolean deleted = tmp.delete();
            if (!deleted) {
                logger.warn("Temporary file {} could not be deleted", tmp.getAbsolutePath());
            }
        }
    }

    public SpeakerEntity getSpeakerById(UUID projectId, String userId) {
        return speakerRepository.findByProjectIdAndUserId(projectId, userId);
    }

    public boolean deleteSpeaker(UUID projectId, String userId) {
        SpeakerEntity speaker = getSpeakerById(projectId, userId);
        if (speaker != null) {
            speakerRepository.delete(speaker);
            logger.info("Deleted speaker with ID: {}", userId);
            return true;
        } else {
            logger.warn("Speaker with ID {} not found in project {}", userId, projectId);
            return false;
        }
    }

    public SpeakerEntity updateSpeaker(
            UUID projectId,
            String userId,
            String userName,
            MultipartFile speakingSample) throws IOException {
        SpeakerEntity speaker = getSpeakerById(projectId, userId);
        if (speaker == null) {
            return null;
        }

        if (userName != null && !userName.isEmpty()) {
            speaker.setUserName(userName);
        }

        if (speakingSample != null && !speakingSample.isEmpty()) {
            speaker.setSpeakingSample(speakingSample.getBytes());
            speaker.setSampleExtension(FilenameUtils.getExtension(speakingSample.getOriginalFilename()));
            speaker.setOriginalFileName(speakingSample.getOriginalFilename());
        }

        return speakerRepository.save(speaker);
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
                "attachment; filename=\"speaking-samples.zip\""
        );

        // 3. Stream the ZIP directly to the response output stream:
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            byte[] buffer = new byte[8192];

            for (SpeakerEntity m : speakers) {
                // Build a predictable, ordered filename:
                String filename = m.getUserId() + "." + m.getSampleExtension();

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

    public Duration getDurationWithFFprobe(File file) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.getAbsolutePath()
        );
        Process p = pb.start();
        try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = out.readLine();
            p.waitFor();
            if (line != null) {
                double seconds = Double.parseDouble(line.trim());
                return Duration.ofMillis((long)(seconds * 1000));
            }
        }
        return Duration.ZERO;
    }
}