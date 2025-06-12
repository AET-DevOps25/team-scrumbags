package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.UUID;

public class DeploymentReviewEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "deployment_review";

    public DeploymentReviewEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        var action = payload.get("action").asText();
        message.getMetadata().setType(EVENT_TYPE + " " + action);
        // required field
        message.getContent().put("since", payload.get("since").asText());

        StringBuilder reviewers = new StringBuilder();
        if (payload.has("reviewers")) {
            payload.get("reviewers").forEach(reviewer ->
                    reviewers.append(
                            "{" +
                                    reviewer.get("reviewer_id").get("id").asText() +
                                    reviewer.get("reviewer_login").get("login").asText()
                                    + "}"
                    ).append(","));
            message.getContent().put("reviewers", reviewers.toString());
        }

        // required field which can be null
        JsonNodeUtils.putTextAtInMap(payload, "workflow_run/id", message.getContent());

        // optional field
        if (payload.has("workflow_job_runs")) {
            StringBuilder workflowJobRuns = new StringBuilder();
            payload.get("workflow_job_runs").forEach(workflowJobRun ->
                    workflowJobRuns.append(workflowJobRun.get("id").asText()).append(","));
            message.getContent().put("workflow_job_runs_ids", workflowJobRuns.toString());
        }


        switch (action) {
            case "approved", "rejected":
                JsonNodeUtils.putTextAtInMap(payload, "comment", message.getContent());

                JsonNodeUtils.putTextAtInMap(payload, "approver/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "approver/login", message.getContent());
                break;
            case "requested":
                message.getContent().put("environment", payload.get("environment").asText());

                JsonNodeUtils.putTextAtInMap(payload, "requestor/id", message.getContent());
                JsonNodeUtils.putTextAtInMap(payload, "requestor/login", message.getContent());
                break;
        }

        return message;
    }
}
