package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class PushEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "push";

    public PushEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        return null;
    }
}
