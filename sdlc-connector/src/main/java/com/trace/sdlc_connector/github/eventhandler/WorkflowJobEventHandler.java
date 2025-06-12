package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class WorkflowJobEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "workflow_job";

    public WorkflowJobEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        // required fields
        message.getContent().put("workflow_job", payload.get("workflow_job").asText());

        // optional fields
        JsonNodeUtils.putTextAtInMap(payload, "deployment", message.getContent());

        return message;
    }
}
