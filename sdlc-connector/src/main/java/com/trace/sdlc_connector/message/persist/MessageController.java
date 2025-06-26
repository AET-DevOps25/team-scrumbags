package com.trace.sdlc_connector.message.persist;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class MessageController {

    private final MessageRepo messageRepo;

    MessageController(MessageRepo messageRepo) {
        this.messageRepo = messageRepo;
    }

    @GetMapping("projects/{projectId}/messages")
    public ResponseEntity<?> retrieveAllData(
            @PathVariable UUID projectId) {
        var entities = messageRepo.findAllByProjectId(projectId);

        return ResponseEntity.ok(entities);
    }
}
