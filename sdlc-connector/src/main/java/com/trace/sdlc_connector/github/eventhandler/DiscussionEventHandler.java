package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DiscussionEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "discussion";

    public DiscussionEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);
        var action = payload.get("action").asText();

        message.getMetadata().setType(EVENT_TYPE + " " + action);

        message.getContent().put("discussion", payload.get("discussion").asText());

        switch (action) {
            case "answered":
                message.getContent().put("answer", payload.get("answer").asText());
                break;
            case "category_changed", "edited", "transferred":
                message.getContent().put("changes", payload.get("changes").asText());
                break;
            case "labeled", "unlabeled":
                message.getContent().put("label", payload.get("label").asText());
                break;
            case "unanswered":
                message.getContent().put("old_answer", payload.get("old_answer").asText());
                break;
        }

        return message;
    }
}
