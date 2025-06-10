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
    public Message handleEvent(UUID projectId, JsonNode payload, Long now) {
        var message = super.handleEvent(projectId, payload, now);

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
        message.getContent().put("workflow_run_id", JsonNodeUtils.nullableMap(payload, "workflow_run", wfr -> wfr.get("id").asText()));

        // optional field
        if (payload.has("workflow_job_runs")) {
            StringBuilder workflowJobRuns = new StringBuilder();
            payload.get("workflow_job_runs").forEach(workflowJobRun ->
                    workflowJobRuns.append(workflowJobRun.get("id").asText()).append(","));
            message.getContent().put("workflow_job_runs_ids", workflowJobRuns.toString());
        }


        switch (action) {
            case "approved", "rejected":
                if (payload.has("comment")) {
                    message.getContent().put("comment", payload.get("comment").asText());
                }
                if (payload.has("approver")) {
                    JsonNodeUtils.optional(payload, "approver", a -> {
                        message.getContent().put("approver_id", a.get("id").asText());
                        message.getContent().put("approver_login", a.get("login").asText());
                    });
                }
                break;
            case "requested":
                message.getContent().put("environment", payload.get("environment").asText());
                message.getContent().put("requestor_id", JsonNodeUtils.nullableMap(payload, "requestor", r -> r.get("id").asText()));
                message.getContent().put("requestor_login", JsonNodeUtils.nullableMap(payload, "requestor", r -> r.get("login").asText()));
                break;
        }

        return message;
    }
}
