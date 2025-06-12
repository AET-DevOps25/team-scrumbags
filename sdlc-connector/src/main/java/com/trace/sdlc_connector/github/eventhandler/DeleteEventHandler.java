package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DeleteEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "delete";

    public DeleteEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("ref_type").asText());

        message.getContent().put("pusher_type", payload.get("pusher_type").asText());
        message.getContent().put("ref", payload.get("ref").asText());

        return message;
    }
}
