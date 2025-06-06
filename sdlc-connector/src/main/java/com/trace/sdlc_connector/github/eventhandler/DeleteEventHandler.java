package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeleteEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "delete";
    private final UserMappingRepo userMappingRepo;

    public DeleteEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("pusher_type", payload.get("pusher_type").asText());
        content.put("ref", payload.get("ref").asText());

        return new Message(
                new Metadata(
                        EVENT_TYPE + " " + payload.get("ref_type").asText(),
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
