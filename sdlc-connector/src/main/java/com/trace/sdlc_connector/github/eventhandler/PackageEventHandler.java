package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PackageEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "package";
    private final UserMappingRepo userMappingRepo;

    public PackageEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();

        content.put("package", payload.get("package").asText());

        return new Message(
                new Metadata(
                        EVENT_TYPE + " " + payload.get("action").asText(),
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
