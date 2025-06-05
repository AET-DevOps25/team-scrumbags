package com.trace.transcription.entity;

import jakarta.persistence.*;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "speaker_entity")
public class SpeakerEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    private UUID projectId;

    private String sampleFilePath;

    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    //todo finish

}
