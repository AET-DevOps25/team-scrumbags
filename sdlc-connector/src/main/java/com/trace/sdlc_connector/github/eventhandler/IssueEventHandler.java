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
                JsonNodeUtils.putTextAtInMap(payload, "assignee/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "assignee/login", message.getContent());
                break;
            case "demilestoned", "milestoned":
                JsonNodeUtils.putTextAtInMap(payload, "milestone/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "milestone/title", message.getContent());
                break;
            case "edited":
                message.getContent().put("changes", payload.get("changes").asText());
                // intended fall-through to handle "label"
            case "labeled", "unlabeled":
                // optional
                JsonNodeUtils.putTextAtInMap(payload, "label/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "label/name", message.getContent());
                break;
            case "opened", "transferred":
                JsonNodeUtils.putTextAtInMap(payload, "changes", message.getContent());
                break;
            case "typed":
                // required nullable field
                JsonNodeUtils.putTextAtInMap(payload, "type", message.getContent());
                break;
        }

        return message;
    }
}
