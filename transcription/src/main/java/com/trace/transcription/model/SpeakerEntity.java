package com.trace.transcription.model;

import jakarta.persistence.*;

@Entity
public class SpeakerEntity {

    @Id
    private String speakerId;

    private String speakerName;

    private String projectId;

    @Lob
    private byte[] speakingSample;

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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public byte[] getSpeakingSample() {
        return speakingSample;
    }

    public void setSpeakingSample(byte[] speakingSample) {
        this.speakingSample = speakingSample;
    }

}
