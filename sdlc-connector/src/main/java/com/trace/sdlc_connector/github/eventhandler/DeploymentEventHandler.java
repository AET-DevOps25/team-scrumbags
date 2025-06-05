package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class DeploymentEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "deployment";

    public DeploymentEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        DataEntity dataEntity = new DataEntity();

        dataEntity.setType("deployment");

        dataEntity.getData().put("action", payload.get("action").asText());
        dataEntity.getData().put("deployment", payload.get("deployment").asText());
        dataEntity.getData().put("sender", payload.get("sender").asText());
        dataEntity.getData().put("workflow", payload.get("workflow").asText());
        dataEntity.getData().put("workflow_run", payload.get("workflow_run").asText());

        return dataEntity;
    }
}
