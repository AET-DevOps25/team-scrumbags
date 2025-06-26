package com.trace.sdlc_connector.message;

import java.util.UUID;

public class Metadata {
    private UUID eventId;
    private String type;
    private final UUID userId;
    private final long timestamp;
    private final UUID projectId;

    public Metadata(UUID eventId, String type, UUID userId, long timestamp, UUID projectId) {
        this.eventId = eventId;
        this.type = type;
        this.userId = userId;
        this.timestamp = timestamp;
        this.projectId = projectId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
