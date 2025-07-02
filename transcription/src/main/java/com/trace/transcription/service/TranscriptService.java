package com.trace.transcription.service;

import com.trace.transcription.dto.TranscriptInput;
import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.model.TranscriptEntity;
import com.trace.transcription.dto.TranscriptSegment;
import com.trace.transcription.repository.SpeakerRepository;
import com.trace.transcription.repository.TranscriptRepository;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public void saveFromJson(String json) throws Exception {
        List<TranscriptInput> inputList = mapper.readValue(json, new TypeReference<>() {});

        TranscriptInput.Metadata meta = inputList.getFirst().metadata;
        List<TranscriptSegment> segments = inputList.stream()
                .map(input -> new TranscriptSegment(
                        input.content.index,
                        input.content.text,
                        input.content.start,
                        input.content.end,
                        input.content.speaker_id,
                        input.content.speaker
                ))
                .collect(Collectors.toList());

        TranscriptEntity entity = new TranscriptEntity(UUID.randomUUID(), segments, meta.project_id, meta.timestamp);
        transcriptRepository.save(entity);
    }

    public String transcriptAsyncLocal(UUID projectId, MultipartFile file, long timestamp) throws IOException, InterruptedException {
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
            for (SpeakerEntity speaker : speakers) {
                byte[] audio = speaker.getSpeakingSample();
                String ext = speaker.getSampleExtension();
                if (audio != null && audio.length > 0) {
                    Path spath = tempDir.resolve("sample-" + speaker.getName() + "_" + speaker.getId() + "." + ext);
                    Files.write(spath, audio, StandardOpenOption.CREATE);
                }
            }

            // Launch Python process
            ProcessBuilder pb = new ProcessBuilder("python3", "transcriber.py", tempDir.toString(), String.valueOf(timestamp))
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
                throw new RuntimeException("Python exited with code " + exitCode + ". Check logs for details.");
            }

            // Read JSON output from file
            Path resultPath = tempDir.resolve("transcription_result.json");
            if (!Files.exists(resultPath)) {
                throw new RuntimeException("Transcription result file not found: " + resultPath);
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
}
