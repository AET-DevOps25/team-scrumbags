package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class WorkflowDispatchEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "workflow_dispatch";

    public WorkflowDispatchEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE);

        // required fields
        message.getContent().put("ref", payload.get("ref").asText());
        message.getContent().put("workflow", payload.get("workflow").asText());

        // required but nullable fields
        JsonNodeUtils.putTextAtInMap(payload, "inputs", message.getContent());

        return message;
    }
}
