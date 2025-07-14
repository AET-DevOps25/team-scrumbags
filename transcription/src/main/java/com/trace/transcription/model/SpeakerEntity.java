package com.trace.transcription.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "speaker_entity")
public class SpeakerEntity {

    @Id
    private String userId;

    private String userName;

    private UUID projectId;

    @Lob
    @JsonIgnore
    private byte[] speakingSample;

    private String sampleExtension;

    private String originalFileName;

    public SpeakerEntity() {}

    public SpeakerEntity(String userId, String userName, UUID projectId, byte[] bytes, String extension, String originalFileName) {
        this.userId = userId;
        this.userName = userName;
        this.projectId = projectId;
        this.speakingSample = bytes;
        this.sampleExtension = extension;
        this.originalFileName = originalFileName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public byte[] getSpeakingSample() {
        return speakingSample;
    }

    public void setSpeakingSample(byte[] speakingSample) {
        this.speakingSample = speakingSample;
    }

    public String getSampleExtension() {
        return sampleExtension;
    }

    public void setSampleExtension(String sampleExtension) {
        this.sampleExtension = sampleExtension;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
}
