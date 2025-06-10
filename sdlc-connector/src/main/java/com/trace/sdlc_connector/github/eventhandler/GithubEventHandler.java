package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.Message;
import com.trace.sdlc_connector.Metadata;
import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// reference: https://docs.github.com/en/webhooks/webhook-events-and-payloads
public abstract class GithubEventHandler {
    private final String eventType;
    private final UserMappingRepo userMappingRepo;

    public GithubEventHandler(String eventType, UserMappingRepo userMappingRepo) {
        this.eventType = eventType;
        this.userMappingRepo = userMappingRepo;
    }

    public String getEventType() {
        return eventType;
    }

    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        Map<String, Object> content = new HashMap<>();
        content.put("platform", SupportedSystem.GITHUB);

        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        return new Message(
                new Metadata(
                        null,
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
