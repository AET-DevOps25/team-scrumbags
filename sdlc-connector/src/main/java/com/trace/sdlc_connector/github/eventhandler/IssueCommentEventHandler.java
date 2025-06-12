package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class IssueCommentEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "issue_comment";

    public IssueCommentEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        var action = payload.get("action").asText();

        message.getMetadata().setType(EVENT_TYPE + " " + action);

        // required fields
        message.getContent().put("comment", payload.get("comment").asText());
        message.getContent().put("issue_id", payload.get("issue").get("id").asText());
        message.getContent().put("issue_title", payload.get("title").get("id").asText());

        switch (action) {
            case "edited":
                // required field
                message.getContent().put("changes", payload.get("changes").asText());
                break;
        }

        return message;
    }
}
