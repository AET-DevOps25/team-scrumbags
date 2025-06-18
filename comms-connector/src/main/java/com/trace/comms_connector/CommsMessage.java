package com.trace.comms_connector;

import java.util.UUID;

public interface CommsMessage {
    public String getJsonString(UUID userId, UUID projectId);
}
