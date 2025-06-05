package com.trace.sdlc_connector.github.eventhandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;

// reference: https://docs.github.com/en/webhooks/webhook-events-and-payloads
public abstract class GithubEventHandler {
    private final String eventType;

    public GithubEventHandler(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }

    public abstract DataEntity handleEvent(JsonNode payload);
}
