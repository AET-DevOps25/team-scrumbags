package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PushEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "push";
    private final UserMappingRepo userMappingRepo;

    public PushEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("after", payload.get("after").asText());
        content.put("base_ref", payload.get("base_ref").asText());
        content.put("before", payload.get("before").asText());
        content.put("commits", payload.get("commits").asText());
        content.put("compare", payload.get("compare").asText());
        content.put("created", payload.get("created").asText());
        content.put("deleted", payload.get("deleted").asText());
        content.put("forced", payload.get("forced").asText());
        content.put("head_commit", payload.get("head_commit").asText());
        content.put("pusher", payload.get("pusher").asText());
        content.put("ref", payload.get("ref").asText());

        return new Message(
                new Metadata(
                        EVENT_TYPE,
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
