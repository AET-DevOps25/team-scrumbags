package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class IssueEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "issues";

    public IssueEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

        var action = payload.get("action").asText();
        message.getMetadata().setType(EVENT_TYPE + " " + action);

        message.getContent().put("issue_id", payload.get("issue").get("id").asText());
        message.getContent().put("issue_title", payload.get("issue").get("title").asText());

        // optional fields
        switch (action) {
            case "assigned", "unassigned":
                // optional and nullable
                if (payload.has("assignee")) {
                    message.getContent().put("assignee_id", JsonNodeUtils.nullableMap(payload, "assignee", a -> a.get("id").asText()));
                    message.getContent().put("assignee_login", JsonNodeUtils.nullableMap(payload, "assignee", a -> a.get("login").asText()));
                }
                break;
            case "demilestoned", "milestoned":
                if (payload.has("milestone")) {
                    message.getContent().put("milestone_id", JsonNodeUtils.nullableMap(payload, "milestone", m -> m.get("id").asText()));
                    message.getContent().put("milestone_title", JsonNodeUtils.nullableMap(payload, "milestone", m -> m.get("title").asText()));
                }
                break;
            case "edited":
                message.getContent().put("changes", payload.get("changes").asText());
                // intended fall-through to handle "label"
            case "labeled", "unlabeled":
                // optional
                JsonNodeUtils.optional(payload, "label", label -> {
                    message.getContent().put("label_id", label.get("id").asText());
                    message.getContent().put("label_name", label.get("name").asText());
                });
                break;
            case "opened", "transferred":
                if (payload.has("changes")) {
                    message.getContent().put("changes", payload.get("changes").asText());
                }
                break;
            case "typed":
                // required nullable field
                message.getContent().put("type", JsonNodeUtils.nullableMap(payload, "type", t -> t.asText()));
                break;
        }

        return message;
    }
}
