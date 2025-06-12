package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class PullRequestEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "pull_request";

    public PullRequestEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.read("$.action", String.class));

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.number", "$.pull_request", "$.changes",
                        "$.assignee.id", "$.assignee.login", "$.reason",
                        "$.milestone.id", "$.milestone.title", "$.label.id", "$.label.name",
                        "$.requested_reviewer.id", "$.requested_reviewer.login",
                        "$.before", "$.after")
        );

        return message;
    }
}
