package com.trace.comms_connector.model;

import java.util.UUID;

public abstract class CommsMessage {
    public abstract String getJsonString(UUID userId, UUID projectId);

    public abstract String getId();

    public abstract CommsPlatformUser getAuthor();
}
