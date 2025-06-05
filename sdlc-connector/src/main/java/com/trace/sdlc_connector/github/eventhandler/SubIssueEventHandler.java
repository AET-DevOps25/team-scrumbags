package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

public class SubIssueEventHandler extends GithubEventHandler{

    private static final String EVENT_TYPE = "sub_issues";

    public SubIssueEventHandler() {
        super(EVENT_TYPE);
    }

    @Override
    public DataEntity handleEvent(JsonNode payload) {
        return null;
    }
}
