package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IssueCommentEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "issue_comment";
    private final UserMappingRepo userMappingRepo;

    public IssueCommentEventHandler(UserMappingRepo userMappingRepo) {
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

        content.put("comment", payload.get("comment").asText());
        content.put("issue_id", payload.get("issue").get("id").asText());
        content.put("issue_title", payload.get("title").get("id").asText());

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
