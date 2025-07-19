package com.trace.comms_connector.model;

import java.util.List;
import java.util.UUID;

public interface CommsPlatformRestClient {
    public List<? extends CommsMessage> getChannelMessages(String channelId, String lastMessageId, UUID projectId);
}
