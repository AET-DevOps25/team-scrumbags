package com.trace.sdlc_connector.message.persist;

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

    protected MessageEntity() {
    }

    public MessageEntity(UUID id, String type, UUID userId, Date timestamp, UUID projectId, Map<String, Object> content) {
        this.id = id;
        this.type = type;
        this.userId = userId;
        this.timestamp = timestamp;
        this.projectId = projectId;
        this.content = content;
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