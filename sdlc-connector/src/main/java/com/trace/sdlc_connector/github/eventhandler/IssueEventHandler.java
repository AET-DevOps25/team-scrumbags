package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class IssueEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "issues";

    public IssueEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.read("$.action", String.class));

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.issue.id", "$.issue.title", "$.assignee.id", "$.assignee.login",
                        "$.milestone.id", "$.milestone.title", "$.label.id", "$.label.name", "$.changes", "$.type")
        );

        return message;
    }
}
