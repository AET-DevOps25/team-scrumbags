package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscussionEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "discussion";
    private final UserMappingRepo userMappingRepo;

    public DiscussionEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE);
        this.userMappingRepo = userMappingRepo;
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        UUID userId = userMappingRepo.findById(new UserMapping.UserMappingId(
                projectId, SupportedSystem.GITHUB, payload.get("sender").get("id").asText()
        )).orElseThrow().getUserId();

        Map<String, Object> content = new HashMap<>();
        var action = payload.get("action").asText();

        content.put("discussion", payload.get("discussion").asText());

        switch (action) {
            case "answered":
                content.put("answer", payload.get("answer").asText());
                break;
            case "category_changed", "edited", "transferred":
                content.put("changes", payload.get("changes").asText());
                break;
            case "labeled", "unlabeled":
                content.put("label", payload.get("label").asText());
                break;
            case "unanswered":
                content.put("old_answer", payload.get("old_answer").asText());
                break;
        }

        return new Message(
                new Metadata(
                        EVENT_TYPE + " " + action,
                        userId,
                        now,
                        projectId
                ),
                content
        );
    }
}
