package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatusEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "status";
    private final UserMappingRepo userMappingRepo;

    public StatusEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("id", payload.get("id").asText());
        content.put("name", payload.get("name").asText());
        content.put("description", payload.get("description").asText());
        content.put("context", payload.get("context").asText());
        content.put("state", payload.get("state").asText());
        content.put("commit_sha", payload.get("sha").asText());
        // TODO branches

        return new Message(
                new Metadata(
                        EVENT_TYPE,
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
