package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class PullRequestReviewThreadEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "pull_request_review_thread";

    public PullRequestReviewThreadEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        var action = payload.get("action").asText();
        message.getMetadata().setType(EVENT_TYPE + " " + action);

        // required fields
        message.getContent().put("thread", payload.get("thread").asText());
        message.getContent().put("pull_request_id", payload.get("pull_request").get("id").asText());
        message.getContent().put("pull_request_title", payload.get("pull_request").get("title").asText());

        return message;
    }
}
