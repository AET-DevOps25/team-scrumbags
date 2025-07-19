package com.trace.comms_connector.model;

import java.util.UUID;

import com.trace.comms_connector.Platform;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class GenAiMessage {
    private Metadata metadata;
    private Content content;

    public GenAiMessage(
        String type,
        UUID user,
        long timestamp,
        UUID projectId,
        Platform platform,
        String message,
        String platformUserId,
        String platformGlobalName
    ) {
        this.metadata = new Metadata(type, user, timestamp, projectId);
        this.content = new Content(platform, message, platformUserId, platformGlobalName);
    }

    @AllArgsConstructor @Getter
    private final class Metadata {
        private String type;
        private UUID user;
        private long timestamp;
        private UUID projectId;
    }

    @AllArgsConstructor @Getter
    private final class Content {
        private Platform platform;
        private String message;
        private String platformUserId;
        private String platformGlobalName;
    }
}
