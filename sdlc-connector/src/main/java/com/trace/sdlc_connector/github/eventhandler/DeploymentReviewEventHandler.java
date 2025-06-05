package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class DeploymentReviewEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "deployment_review";

    public DeploymentReviewEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        DataEntity dataEntity = new DataEntity();

        dataEntity.setType("deployment_review");

        dataEntity.getData().put("action", payload.get("action").asText());
        dataEntity.getData().put("approver", payload.get("approver").get("login").asText());
        StringBuilder reviewers = new StringBuilder();
        payload.get("reviewers").forEach(reviewer ->
                reviewers.append(reviewer.get("reviewer").get("login").asText()).append(","));
        dataEntity.getData().put("reviewer", reviewers.toString());
        dataEntity.getData().put("sender", payload.get("sender").asText());
        dataEntity.getData().put("workflow", payload.get("workflow").asText());
        dataEntity.getData().put("workflow_run", payload.get("workflow_run").asText());
        StringBuilder workflowJobRuns = new StringBuilder();
        payload.get("workflow_job_runs").forEach(workflowJobRun ->
                workflowJobRuns.append(workflowJobRun.asText()).append(","));
        dataEntity.getData().put("workflow_job_runs", workflowJobRuns.toString());


        return dataEntity;
    }
}
