package com.trace.transcription.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "speaker_entity")
public class SpeakerEntity {

    @Id
    private String id;

    private String name;

    private UUID projectId;

    @Lob
    private byte[] speakingSample;

    private String sampleExtension;

    public SpeakerEntity() {}

    public SpeakerEntity(String speakerId, String speakerName, UUID projectId, byte[] bytes, String extension) {
        this.id = speakerId;
        this.name = speakerName;
        this.projectId = projectId;
        this.speakingSample = bytes;
        this.sampleExtension = extension;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
