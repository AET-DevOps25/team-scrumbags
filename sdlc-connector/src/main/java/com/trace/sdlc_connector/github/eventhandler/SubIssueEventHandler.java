package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SubIssueEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "sub_issues";

    public SubIssueEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        // required fields
        message.getContent().put("parent_issue_id", payload.get("parent_issue_id").asText());
        message.getContent().put("sub_issue_id", payload.get("sub_issue_id").asText());
        message.getContent().put("sub_issue", payload.get("sub_issue").asText());

        return message;
    }
}
