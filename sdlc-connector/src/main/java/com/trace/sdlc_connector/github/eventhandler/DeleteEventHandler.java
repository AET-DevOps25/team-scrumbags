package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DeleteEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "delete";

    public DeleteEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.read("$.ref_type", String.class));

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.sender.id", "$.sender.login", "$.pusher_type", "$.ref")
        );

        return message;
    }
}
