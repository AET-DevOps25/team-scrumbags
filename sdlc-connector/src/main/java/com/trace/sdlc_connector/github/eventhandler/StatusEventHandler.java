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

    public StatusEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        message.getMetadata().setType(EVENT_TYPE);

        // required fields
        message.getContent().put("id", payload.get("id").asText());
        message.getContent().put("name", payload.get("name").asText());
        message.getContent().put("context", payload.get("context").asText());
        message.getContent().put("state", payload.get("state").asText());
        message.getContent().put("commit_sha", payload.get("sha").asText());
        message.getContent().put("created_at", payload.get("created_at").asText());
        message.getContent().put("updated_at", payload.get("updated_at").asText());

        StringBuilder branches = new StringBuilder();
        payload.get("branches").forEach(branch ->
                branches.append(branch.asText()).append(","));
        message.getContent().put("branches", branches.toString());

        // required but nullable fields
        message.getContent().put("description", JsonNodeUtils.nullableMap(payload, "description", JsonNode::asText));
        message.getContent().put("target_url", JsonNodeUtils.nullableMap(payload, "target_url", JsonNode::asText));

        return message;
    }
}
