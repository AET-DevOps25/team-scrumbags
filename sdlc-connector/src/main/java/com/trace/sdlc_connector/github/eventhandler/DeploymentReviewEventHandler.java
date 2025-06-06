package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeploymentReviewEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "deployment_review";
    private final UserMappingRepo userMappingRepo;

    public DeploymentReviewEventHandler(UserMappingRepo userMappingRepo) {
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

        StringBuilder reviewers = new StringBuilder();
        payload.get("reviewers").forEach(reviewer ->
                reviewers.append(
                        "{" +
                                reviewer.get("reviewer_id").get("id").asText() +
                                reviewer.get("reviewer_login").get("login").asText()
                                + "}"
                ).append(","));
        content.put("reviewer", reviewers.toString());
        content.put("workflow_id", payload.get("workflow").get("id").asText());
        content.put("workflow_run_id", payload.get("workflow_run").get("id").asText());
        StringBuilder workflowJobRuns = new StringBuilder();
        payload.get("workflow_job_runs").forEach(workflowJobRun ->
                workflowJobRuns.append(workflowJobRun.get("id").asText()).append(","));
        content.put("workflow_job_runs_ids", workflowJobRuns.toString());
        content.put("since", payload.get("since").asText());


        switch (action) {
            case "approved", "rejected":
                content.put("comment", payload.get("comment").asText());
                content.put("approver_id", payload.get("approver").get("id").asText());
                content.put("approver_login", payload.get("approver").get("login").asText());
                break;
            case "requested":
                content.put("requestor_id", payload.get("requestor").get("id").asText());
                content.put("requestor_login", payload.get("requestor").get("login").asText());
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
