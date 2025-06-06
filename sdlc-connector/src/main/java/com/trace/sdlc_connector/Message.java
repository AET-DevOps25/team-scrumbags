package com.trace.sdlc_connector;

import java.util.Map;

public class Message {
    private final Metadata metadata;
    private final Map<String, Object> content;

    public Message(Metadata metadata, Map<String, Object> content) {
        this.metadata = metadata;
        this.content = content;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Map<String, Object> getContent() {
        return content;
    }
}
