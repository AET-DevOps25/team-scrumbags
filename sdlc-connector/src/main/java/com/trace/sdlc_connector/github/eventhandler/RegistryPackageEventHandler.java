package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class RegistryPackageEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "registry_package";

    public RegistryPackageEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.get("action").asText());

        message.getContent().put("registry_package", payload.get("registry_package").asText());

        return message;
    }
}
