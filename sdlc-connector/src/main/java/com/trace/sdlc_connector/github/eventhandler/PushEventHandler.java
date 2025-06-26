package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class PushEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "push";

    public PushEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE);

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.sender.id", "$.sender.login", "$.after", "$.before", "$.commits",
                        "$.compare", "$.created", "$.deleted", "$.forced", "$.pusher", "$.ref",
                        "$.base_ref", "$.head_commit")
        );

        return message;
    }
}
