package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PullRequestReviewEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "pull_request_review";
    private final UserMappingRepo userMappingRepo;

    public PullRequestReviewEventHandler(UserMappingRepo userMappingRepo) {
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

        content.put("review", payload.get("review").asText());
        content.put("pull_request_id", payload.get("pull_request").get("id").asText());
        content.put("pull_request_title", payload.get("pull_request").get("title").asText());

        switch (action) {
            case "edited":
                content.put("changes", payload.get("changes").asText());
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
