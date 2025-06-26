package com.trace.sdlc_connector.message;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "message_entity")
public class MessageEntity {

    @Id
    private UUID id;

    private String type;

    private UUID userId;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> content = new HashMap<>();

    public MessageEntity() {
    }

    public MessageEntity(Message message) {
        this.id = message.getMetadata().getEventId();
        this.type = message.getMetadata().getType();
        this.userId = message.getMetadata().getUserId();
        this.timestamp = new Date(message.getMetadata().getTimestamp());
        this.projectId = message.getMetadata().getProjectId();
        this.content = message.getContent();
    }

    public Message toMessage() {
        Metadata metadata = new Metadata(this.id, this.type, this.userId, this.timestamp.getTime(), this.projectId);
        return new Message(metadata, this.content);
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public UUID getUserId() {
        return userId;
    }
}