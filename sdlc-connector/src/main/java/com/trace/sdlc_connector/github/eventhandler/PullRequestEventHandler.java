package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class PullRequestEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "pull_request";

    public PullRequestEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        var action = payload.get("action").asText();
        message.getMetadata().setType(EVENT_TYPE + " " + action);

        message.getContent().put("number", payload.get("number").asText());
        message.getContent().put("pull_request", payload.get("pull_request").asText());

        switch (action) {
            case "assigned", "unassigned":
                // required but nullable
                JsonNodeUtils.putTextAtInMap(payload, "assignee/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "assignee/login", message.getContent());
                break;
            case "auto_merge_disabled", "auto_merge_enabled":
                // required
                message.getContent().put("reason", payload.get("reason").asText());
                break;
            case "demilestoned", "milestoned":
                // optional
                JsonNodeUtils.putTextAtInMap(payload, "milestone/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "milestone/title", message.getContent());
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
                JsonNodeUtils.putTextAtInMap(payload, "label/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "label/name", message.getContent());
                break;
            case "review_request_removed", "review_requested":
                // required but nullable
                JsonNodeUtils.putTextAtInMap(payload, "requested_reviewer/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "requested_reviewer/login", message.getContent());
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
