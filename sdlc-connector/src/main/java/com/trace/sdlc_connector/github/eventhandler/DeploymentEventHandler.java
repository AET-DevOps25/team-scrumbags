package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DeploymentEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "deployment";

    public DeploymentEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        message.getContent().put("deployment", payload.get("deployment").asText());
        // required but nullable fields
        message.getContent().put("workflow_id", JsonNodeUtils.nullableMap(payload, "workflow", wf -> wf.get("id").asText()));
        message.getContent().put("workflow_name", JsonNodeUtils.nullableMap(payload, "workflow", wf -> wf.get("name").asText()));
        message.getContent().put("workflow_run_id", JsonNodeUtils.nullableMap(payload, "workflow_run", wr -> wr.get("id").asText()));

        return message;
    }
}
