package com.trace.transcription.model;

import com.trace.transcription.dto.TranscriptSegment;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
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
    private UUID id;;

    @ElementCollection
    @CollectionTable(name = "transcript_segments", joinColumns = @JoinColumn(name = "transcript_id"))
    private List<TranscriptSegment> content;

    @Column(columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Lob
    private byte[] audio;

    private String audioExtension;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    public TranscriptEntity() {}

    public TranscriptEntity(UUID id, List<TranscriptSegment> content, UUID projectId, byte[] bytes, String extension, long timestamp) {
        this.id = id;
        this.content = content;
        this.projectId = projectId;
        this.audio = bytes;
        this.audioExtension = extension;
        this.timestamp = new Date(timestamp);
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

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
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
}
