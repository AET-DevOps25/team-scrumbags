package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PullRequestEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "pull_request";
    private final UserMappingRepo userMappingRepo;

    public PullRequestEventHandler(UserMappingRepo userMappingRepo) {
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

        content.put("number", payload.get("number").asText());
        content.put("pull_request", payload.get("pull_request").asText());

        switch (action) {
            case "assigned", "unassigned":
                content.put("assignee_id", payload.get("assignee").get("id").asText());
                content.put("assignee_login", payload.get("assignee").get("login").asText());
                break;
            case "auto_merge_disabled", "auto_merge_enabled":
                content.put("reason", payload.get("reason").asText());
                break;
            case "demilestoned", "milestoned":
                content.put("milestone_id", payload.get("milestone").get("id").asText());
                content.put("milestone_title", payload.get("milestone").get("title").asText());
                break;
            case "dequeued", "enqueued":
                content.put("reason", payload.get("reason").asText());
                break;
            case "edited":
                content.put("changes", payload.get("changes").asText());
                break;
            case "labeled", "unlabeled":
                content.put("label_id", payload.get("label").get("id").asText());
                content.put("label_name", payload.get("label").get("name").asText());
                break;
            case "review_request_removed", "review_requested":
                content.put("requested_reviewer_id", payload.get("requested_reviewer").get("id").asText());
                content.put("requested_reviewer_login", payload.get("requested_reviewer").get("login").asText());
                break;
            case "synchronize":
                content.put("before", payload.get("before").asText());
                content.put("after", payload.get("after").asText());
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
