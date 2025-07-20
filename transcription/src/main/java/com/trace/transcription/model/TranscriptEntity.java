package com.trace.transcription.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trace.transcription.dto.TranscriptSegment;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transcript_entity")
public class TranscriptEntity {

    @Id
    @GeneratedValue                         // ← use the default generator
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ElementCollection
    @CollectionTable(name = "transcript_segments", joinColumns = @JoinColumn(name = "transcript_id"))
    private List<TranscriptSegment> content;

    @Column(columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Lob
    @JsonIgnore
    private byte[] audio;

    private String audioExtension;

    @Temporal(TemporalType.TIMESTAMP)
    private long timestamp;

    private boolean isLoading;

    public TranscriptEntity() {}

    public TranscriptEntity(UUID id, List<TranscriptSegment> content, UUID projectId, byte[] bytes, String extension, long timestamp, boolean isLoading) {
        this.id = id;
        this.content = content;
        this.projectId = projectId;
        this.audio = bytes;
        this.audioExtension = extension;
        this.timestamp = timestamp;
        this.isLoading = isLoading;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public List<TranscriptSegment> getContent() {
        return content;
    }

    public void setContent(List<TranscriptSegment> content) {
        this.content = content;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getAudio() {
        return audio;
    }

    public void setAudio(byte[] audio) {
        this.audio = audio;
    }

    public String getAudioExtension() {
        return audioExtension;
    }

    public void setAudioExtension(String audioExtension) {
        this.audioExtension = audioExtension;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setIsLoading(boolean isLoading) {
        this.isLoading = isLoading;
    }
}
