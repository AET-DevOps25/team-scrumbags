package com.trace.transcription.service;

import com.trace.transcription.controller.SpeakerController;
import com.trace.transcription.dto.TranscriptInput;
import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.model.TranscriptEntity;
import com.trace.transcription.dto.TranscriptSegment;
import com.trace.transcription.repository.SpeakerRepository;
import com.trace.transcription.repository.TranscriptRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.trace.transcription.controller.TranscriptController.logger;

@Service
public class TranscriptService {

    private final TranscriptRepository transcriptRepository;
    private final SpeakerRepository speakerRepository;
    private final ObjectMapper mapper;

    public TranscriptService(TranscriptRepository transcriptRepository, SpeakerRepository speakerRepository, ObjectMapper mapper) {
        this.transcriptRepository = transcriptRepository;
        this.speakerRepository = speakerRepository;
        this.mapper = mapper;
    }

    /**
     * Creates and persists a loading entity with isLoading=true.
     */
    public TranscriptEntity createLoadingEntity(UUID projectId, byte[] audio, String extension, long timestamp) {
        TranscriptEntity entity = new TranscriptEntity(
                null,
                null,
                projectId,
                audio,
                extension,
                timestamp,
                true
        );
        return transcriptRepository.save(entity);
    }


    public void saveFromJson(String json, MultipartFile file) throws Exception {
        List<TranscriptInput> inputList = mapper.readValue(json, new TypeReference<>() {});

        TranscriptInput.Metadata meta = inputList.getFirst().metadata;
        List<TranscriptSegment> segments = inputList.stream()
                .map(input -> new TranscriptSegment(
                        input.content.index,
                        input.content.text,
                        input.content.start,
                        input.content.end,
                        input.content.userName,
                        input.content.userId
                ))
                .collect(Collectors.toList());

        String extension = FilenameUtils.getExtension(file.getOriginalFilename());

        TranscriptEntity entity = new TranscriptEntity(null, segments, meta.projectId, file.getBytes(), extension, meta.timestamp, false);
        transcriptRepository.save(entity);
    }

    /**
     * Updates an existing entity by reading JSON, saving segments, and marking isLoading=false.
     */
    @Transactional
    public void updateEntityWithTranscript(UUID transcriptId, String json) throws Exception {
        // Parse JSON into segments and metadata
        List<TranscriptInput> inputList = mapper.readValue(json, new TypeReference<>() {});
        // Map to TranscriptSegment list
        List<TranscriptSegment> segments = inputList.stream()
                .map(input -> new TranscriptSegment(
                        input.content.index,
                        input.content.text,
                        input.content.start,
                        input.content.end,
                        input.content.userName,
                        input.content.userId
                ))
                .collect(Collectors.toList());

        // Fetch entity
        TranscriptEntity entity = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new IllegalArgumentException("Transcript not found"));

        // Update fields
        entity.setContent(segments);
        entity.setIsLoading(false);

        transcriptRepository.save(entity);
    }

    public String transcriptAsync(UUID projectId, MultipartFile file, int speakerAmount, long timestamp) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("media-" + projectId + "_" + UUID.randomUUID() + "-");
        try {
            // Save incoming file
            String cleanFileName = Paths.get(Objects.requireNonNull(file.getOriginalFilename()))
                    .getFileName().toString();
            Path incomingPath = tempDir.resolve(cleanFileName);
            Files.copy(file.getInputStream(), incomingPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Stored incoming file: {} for project {}", cleanFileName, projectId);

            // Dump speaker samples
            List<SpeakerEntity> speakers = speakerRepository.findAllByProjectId(projectId);
            if (speakers.isEmpty()) {
                logger.warn("No speakers found for project {}", projectId);
            } else {
                for (SpeakerEntity speaker : speakers) {
                    byte[] audio = speaker.getSpeakingSample();
                    String ext = speaker.getSampleExtension();
                    if (audio != null && audio.length > 0) {
                        Path spath = tempDir.resolve("sample-" + speaker.getUserName() + "_" + speaker.getUserId() + "." + ext);
                        Files.write(spath, audio, StandardOpenOption.CREATE);
                    }
                }
            }

            // Launch Python process
            ProcessBuilder pb = new ProcessBuilder("python3", "transcriber.py", tempDir.toString(), Integer.toString(speakerAmount), String.valueOf(timestamp))
                    .redirectErrorStream(true);

            Process proc = pb.start();

            // Log output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Transcriber] {}", line);
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                return null;
            }

            // Read JSON output from file
            Path resultPath = tempDir.resolve("transcription_result.json");
            if (!Files.exists(resultPath)) {
                return null;
            }

            String transcriptJson = Files.readString(resultPath, StandardCharsets.UTF_8);
            logger.info("Read transcript from file: {}", resultPath);
            return transcriptJson;
        } finally {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(f -> {
                            if (!f.delete()) {
                                logger.warn("Failed to delete file: {}", f.getAbsolutePath());
                            }
                        });
                logger.info("Cleaned up temp directory: {}", tempDir);
            } catch (IOException e) {
                logger.error("Failed to clean up temp directory: {}", tempDir, e);
            }
        }
    }

    public List<TranscriptEntity> getAllTranscripts(UUID projectId) {
        return transcriptRepository.findAllByProjectId(projectId);
    }

    public void streamAllAudios(UUID projectId, HttpServletResponse response) {
        List<TranscriptEntity> transcripts = transcriptRepository.findAllByProjectId(projectId);
        if (transcripts.isEmpty()) {
            SpeakerController.logger.warn("No transcripts found for project {}", projectId);
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

            for (TranscriptEntity m : transcripts) {
                // Build a predictable, ordered filename:
                String filename = m.getId() + "." + m.getAudioExtension();

                // Add a new ZIP entry
                zos.putNextEntry(new ZipEntry(filename));

                // Write the byte[] LOB in chunks
                try (ByteArrayInputStream in = new ByteArrayInputStream(m.getAudio())) {
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
            SpeakerController.logger.error("Error creating ZIP file for project {}: {}", projectId, e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
