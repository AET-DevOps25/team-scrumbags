package com.trace.transcription.model;

import jakarta.persistence.*;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "transcript_entity")
public class TranscriptEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Lob
    private String content;
    private String projectId;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String id) {
        this.projectId = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
