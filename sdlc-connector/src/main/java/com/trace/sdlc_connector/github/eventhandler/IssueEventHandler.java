package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IssueEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "issues";
    private final UserMappingRepo userMappingRepo;

    public IssueEventHandler(UserMappingRepo userMappingRepo) {
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

        content.put("issue_id", payload.get("issue").get("id").asText());
        content.put("issue_title", payload.get("issue").get("title").asText());

        // optional fields
        switch (action) {
            case "assigned", "unassigned":
                content.put("assignee_id", payload.get("assignee").get("id").asText());
                content.put("assignee_login", payload.get("assignee").get("login").asText());
                break;
            case "demilestoned", "milestoned":
                content.put("milestone_id", payload.get("milestone").get("id").asText());
                content.put("milestone_title", payload.get("milestone").get("title").asText());
                break;
            case "edited":
                content.put("changes", payload.get("changes").asText());
                content.put("label_id", payload.get("label").get("id").asText());
                content.put("label", payload.get("label").get("name").asText());
                break;
            case "labeled", "unlabeled":
                content.put("label_id", payload.get("label").get("id").asText());
                content.put("label", payload.get("label").get("name").asText());
                break;
            case "opened", "transferred":
                content.put("changes", payload.get("changes").asText());
                break;
            case "typed":
                content.put("type", payload.get("type").asText());
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
