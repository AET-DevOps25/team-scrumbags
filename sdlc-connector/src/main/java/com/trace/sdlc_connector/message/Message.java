package com.trace.sdlc_connector.message;

import com.trace.sdlc_connector.message.persist.MessageEntity;

import java.util.Map;

public class Message {
    private final Metadata metadata;
    private final Map<String, Object> content;

    private Message(){
        metadata = null;
        content = null;
    }

    public Message(Metadata metadata, Map<String, Object> content) {
        this.metadata = metadata;
        this.content = content;
    }

    public Message(MessageEntity messageEntity){
        this.metadata = new Metadata(
                messageEntity.getId(),
                messageEntity.getType(),
                messageEntity.getUserId(),
                messageEntity.getTimestamp().getTime(),
                messageEntity.getProjectId()
        );
        this.content = messageEntity.getContent();
    }

    public MessageEntity toMessageEntity() {
        return new MessageEntity(
                metadata.getEventId(),
                metadata.getType(),
                metadata.getUserId(),
                new java.util.Date(metadata.getTimestamp()),
                metadata.getProjectId(),
                content
        );
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Map<String, Object> getContent() {
        return content;
    }
}
