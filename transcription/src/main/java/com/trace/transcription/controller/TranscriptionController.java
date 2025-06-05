package com.trace.transcription.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;


@RestController
public class TranscriptionController {

    public static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);
    private final Path uploadDir;

    public TranscriptionController(@Value("${file.local-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
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

    //
    @PostMapping("/receive")
    public ResponseEntity<String> receiveFile(@RequestParam("file") MultipartFile file) {
        try {
            // Normalize filename to prevent path traversal
            String cleanFileName = Paths.get(Objects.requireNonNull(file.getOriginalFilename())).getFileName().toString();
            Path targetLocation = uploadDir.resolve(cleanFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("Received and stored: " + cleanFileName);
        } catch (IOException ex) {
            logger.error("Failed to store file: {}", ex.getMessage());
            return ResponseEntity
                    .status(500)
                    .body("Failed to store file: " + ex.getMessage());
        }
    }

}