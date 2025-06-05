package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class CommitCommentEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "commit_comment";

    public CommitCommentEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        DataEntity dataEntity = new DataEntity();

        dataEntity.setType("commit_comment");

        dataEntity.getData().put("comment", payload.get("comment").asText());
        dataEntity.getData().put("action", payload.get("action").asText());
        dataEntity.getData().put("sender", payload.get("sender").asText());

        return dataEntity;
    }
}
