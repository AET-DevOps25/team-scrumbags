package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PullRequestEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "pull_request";

    public PullRequestEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        var action = payload.get("action").asText();
        message.getMetadata().setType(EVENT_TYPE + " " + action);

        message.getContent().put("number", payload.get("number").asText());
        message.getContent().put("pull_request", payload.get("pull_request").asText());

        switch (action) {
            case "assigned", "unassigned":
                // required but nullable
                message.getContent().put("assignee_id", JsonNodeUtils.nullableMap(payload, "assignee", a -> a.get("id").asText()));
                message.getContent().put("assignee_login", JsonNodeUtils.nullableMap(payload, "assignee", a -> a.get("login").asText()));
                break;
            case "auto_merge_disabled", "auto_merge_enabled":
                // required
                message.getContent().put("reason", payload.get("reason").asText());
                break;
            case "demilestoned", "milestoned":
                // optional
                JsonNodeUtils.optional(payload, "milestone", milestone -> {
                    message.getContent().put("milestone_id", milestone.get("id").asText());
                    message.getContent().put("milestone_title", milestone.get("title").asText());
                });
                break;
            case "dequeued":
                // required
                message.getContent().put("reason", payload.get("reason").asText());
                break;
            case "edited":
                // required
                message.getContent().put("changes", payload.get("changes").asText());
                break;
            case "labeled", "unlabeled":
                // optional
                JsonNodeUtils.optional(payload, "label", label -> {
                    message.getContent().put("label_id", label.get("id").asText());
                    message.getContent().put("label_name", label.get("name").asText());
                });
                break;
            case "review_request_removed", "review_requested":
                // required but nullable
                message.getContent().put("requested_reviewer_id", JsonNodeUtils.nullableMap(payload, "requested_reviewer", r -> r.get("id").asText()));
                message.getContent().put("requested_reviewer_login", JsonNodeUtils.nullableMap(payload, "requested_reviewer", r -> r.get("login").asText()));
                break;
            case "synchronize":
                // required
                message.getContent().put("before", payload.get("before").asText());
                message.getContent().put("after", payload.get("after").asText());
                break;
        }

        return message;
    }
}
