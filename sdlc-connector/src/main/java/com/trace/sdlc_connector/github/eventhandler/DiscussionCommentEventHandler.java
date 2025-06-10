package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscussionCommentEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "discussion_comment";

    public DiscussionCommentEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);
        var action = payload.get("action").asText();

        message.getMetadata().setType(EVENT_TYPE + " " + action);

        // required fields
        message.getContent().put("comment", payload.get("comment").asText());
        message.getContent().put("discussion_id", payload.get("discussion").get("id").asText());
        message.getContent().put("discussion_title", payload.get("discussion").get("title").asText());

        switch (action) {
            case "edited":
                message.getContent().put("changes", payload.get("changes").asText());
                break;
        }

        return message;
    }
}
