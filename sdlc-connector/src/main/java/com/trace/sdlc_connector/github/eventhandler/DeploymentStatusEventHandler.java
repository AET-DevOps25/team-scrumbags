package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeploymentStatusEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "deployment_status";
    private final UserMappingRepo userMappingRepo;

    public DeploymentStatusEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("check_run", payload.get("check_run").asText());
        content.put("deployment", payload.get("deployment").asText());
        content.put("deployment_status", payload.get("deployment_status").asText());
        content.put("sender", payload.get("sender").asText());
        content.put("workflow", payload.get("workflow").asText());
        content.put("workflow_run", payload.get("workflow_run").asText());

        return new Message(
                new Metadata(
                        EVENT_TYPE + " " + payload.get("action").asText(),
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
