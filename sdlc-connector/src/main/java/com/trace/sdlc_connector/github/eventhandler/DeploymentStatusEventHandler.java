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
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        // required fields
        message.getContent().put("deployment", payload.get("deployment").asText());
        message.getContent().put("deployment_status", payload.get("deployment_status").asText());

        // optional and possibly null fields
        JsonNodeUtils.putTextAtInMap(payload, "check_run", message.getContent());
        JsonNodeUtils.putTextAtInMap(payload, "workflow/id", message.getContent());
        JsonNodeUtils.putTextAtInMap(payload, "workflow/name", message.getContent());
        JsonNodeUtils.putTextAtInMap(payload, "workflow_run/id", message.getContent());
        JsonNodeUtils.putTextAtInMap(payload, "workflow_run/name", message.getContent());

        return message;
    }
}
