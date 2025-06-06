package com.trace.transcription.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class SpeakerEntity {

    @Id
    private String speakerId;

    private String speakerName;

    private UUID projectId;

    @Lob
    private byte[] speakingSample;

    private String sampleExtension;

    public String getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(String speakerId) {
        this.speakerId = speakerId;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
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
