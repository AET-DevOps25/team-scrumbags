package com.trace.comms_connector;

import java.util.UUID;

public interface CommsMessage {
    public String getMessageJsonStringForGenAi(UUID userId, UUID projectId);
}
