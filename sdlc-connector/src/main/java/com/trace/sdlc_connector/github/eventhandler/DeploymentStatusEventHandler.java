package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DeploymentStatusEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "deployment_status";

    public DeploymentStatusEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        // required fields
        message.getContent().put("deployment", payload.get("deployment").asText());
        message.getContent().put("deployment_status", payload.get("deployment_status").asText());

        // optional and possibly null fields
        if (payload.has("check_run")) {
            message.getContent().put("check_run", JsonNodeUtils.nullableMap(payload, "check_run", cr -> cr.asText()));
        }
        if (payload.has("workflow")) {
            message.getContent().put("workflow_id", JsonNodeUtils.nullableMap(payload, "workflow", wf -> wf.get("id").asText()));
            message.getContent().put("workflow_name", JsonNodeUtils.nullableMap(payload, "workflow", wf -> wf.get("name").asText()));
        }
        if (payload.has("workflow_run")) {
            message.getContent().put("workflow_run_id", JsonNodeUtils.nullableMap(payload, "workflow_run", wr -> wr.get("id").asText()));
            message.getContent().put("workflow_run_name", JsonNodeUtils.nullableMap(payload, "workflow_run", wr -> wr.get("name").asText()));
        }

        return message;
    }
}
