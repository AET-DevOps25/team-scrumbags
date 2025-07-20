package com.trace.sdlc_connector.message;

import java.util.Map;
import java.util.UUID;

public record MessageDTO(
        MetadataDTO metadata,
        Map<String, Object> content
) {
    public MessageDTO(Message message) {
        this(
                new MetadataDTO(message.getMetadata()),
                addEventIdToContent(message.getContent(), message.getMetadata().getEventId())
        );
    }

    private static Map<String, Object> addEventIdToContent(Map<String, Object> content, UUID eventId) {
        if (content != null) {
            content.put("eventId", eventId);
        }
        return content;
    }

    private record MetadataDTO(
            String type,
            UUID user,
            long timestamp,
            UUID projectId
    ) {
        public MetadataDTO(Metadata metadata) {
            this(
                    metadata.getType(),
                    metadata.getUserId(),
                    metadata.getTimestamp() / 1000, // Convert milliseconds to seconds
                    metadata.getProjectId()
            );
        }
    }
}
