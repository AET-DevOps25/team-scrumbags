package com.trace.sdlc_connector;

import java.util.UUID;

public class Metadata {
    private final String type;
    private final UUID userId;
    private final long timestamp;
    private final UUID projectId;

    public Metadata(String type, UUID userId, long timestamp, UUID projectId) {
        this.type = type;
        this.userId = userId;
        this.timestamp = timestamp;
        this.projectId = projectId;
    }

    public String getType() {
        return type;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getProjectId() {
        return projectId;
    }
}
