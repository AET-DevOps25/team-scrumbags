package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatusEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "status";

    public StatusEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE);

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.id", "$.name", "$.context", "$.state",
                        "$.sha", "$.description", "$.target_url", "$.created_at", "$.updated_at")
        );

        List<Map<String, Object>> branches = payload.read("$.branches", List.class);
        if (branches != null) {
            message.getContent().put(
                    "branches", branches
            );
        }

        return message;
    }
}
