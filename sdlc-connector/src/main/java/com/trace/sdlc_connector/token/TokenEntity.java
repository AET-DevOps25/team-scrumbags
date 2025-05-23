package com.trace.sdlc_connector.token;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "token")
public class TokenEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportedSystem supportedSystem;

    @Convert(converter = TokenConverter.class)
    @Column(nullable = false)
    private String token;


    public TokenEntity() {
    }

    public TokenEntity(String token, UUID projectId, SupportedSystem supportedSystem) {
        this.token = token;
        this.projectId = projectId;
        this.supportedSystem = supportedSystem;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public SupportedSystem getSupportedSystem() {
        return supportedSystem;
    }

    public String getToken() {
        return token;
    }
}
