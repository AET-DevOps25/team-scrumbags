package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkflowDispatchEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "workflow_dispatch";
    private final UserMappingRepo userMappingRepo;

    public WorkflowDispatchEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("inputs", payload.get("inputs").asText());
        content.put("ref", payload.get("ref").asText());
        content.put("workflow", payload.get("workflow").asText());

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
