package com.trace.transcription.model;

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
    private byte[] speakingSample;

    private String sampleExtension;

    public SpeakerEntity() {}

    public SpeakerEntity(String userId, String userName, UUID projectId, byte[] bytes, String extension) {
        this.userId = userId;
        this.userName = userName;
        this.projectId = projectId;
        this.speakingSample = bytes;
        this.sampleExtension = extension;
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
}
