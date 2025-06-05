package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class DeploymentStatusEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "deployment_status";

    public DeploymentStatusEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        DataEntity dataEntity = new DataEntity();

        dataEntity.setType("deployment_status");

        dataEntity.getData().put("action", payload.get("action").asText());
        dataEntity.getData().put("check_run", payload.get("check_run").asText());
        dataEntity.getData().put("deployment", payload.get("deployment").asText());
        dataEntity.getData().put("deployment_status", payload.get("deployment_status").asText());
        dataEntity.getData().put("sender", payload.get("sender").asText());
        dataEntity.getData().put("workflow", payload.get("workflow").asText());
        dataEntity.getData().put("workflow_run", payload.get("workflow_run").asText());

        return dataEntity;
    }
}
