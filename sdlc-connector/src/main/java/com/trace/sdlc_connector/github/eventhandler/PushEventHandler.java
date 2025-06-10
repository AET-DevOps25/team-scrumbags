package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PushEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "push";

    public PushEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        message.getMetadata().setType(EVENT_TYPE);

        // required fields
        message.getContent().put("after", payload.get("after").asText());
        message.getContent().put("before", payload.get("before").asText());
        message.getContent().put("commits", payload.get("commits").asText());
        message.getContent().put("compare", payload.get("compare").asText());
        message.getContent().put("created", payload.get("created").asText());
        message.getContent().put("deleted", payload.get("deleted").asText());
        message.getContent().put("forced", payload.get("forced").asText());
        message.getContent().put("pusher", payload.get("pusher").asText());
        message.getContent().put("ref", payload.get("ref").asText());

        // required but nullable fields
        message.getContent().put("base_ref", JsonNodeUtils.nullableMap(payload, "base_ref", JsonNode::asText));
        message.getContent().put("head_commit", JsonNodeUtils.nullableMap(payload, "head_commit", JsonNode::asText));
        return message;
    }
}
