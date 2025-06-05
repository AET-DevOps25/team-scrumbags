package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

import java.util.HashMap;
import java.util.Map;

public class BranchCreateEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "create";

    public BranchCreateEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        DataEntity dataEntity = new DataEntity();

        dataEntity.setType(payload.get("ref_type").asText());

        dataEntity.getData().put("pusher_type", payload.get("pusher_type").asText());
        dataEntity.getData().put("ref", payload.get("ref").asText());
        dataEntity.getData().put("sender", payload.get("sender").asText());

        return dataEntity;
    }
}
