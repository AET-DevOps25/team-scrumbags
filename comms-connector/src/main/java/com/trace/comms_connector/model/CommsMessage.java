package com.trace.comms_connector.model;

import java.util.UUID;

public interface CommsMessage {
    public String getJsonString(UUID userId, UUID projectId);

    public String getId();

    public CommsPlatformUser getAuthor();
}
