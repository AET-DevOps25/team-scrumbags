package com.trace.sdlc_connector.token;

import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.user.UserMapping;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "token")
@IdClass(TokenEntity.TokenEntityId.class)
public class TokenEntity {

    public static class TokenEntityId implements Serializable {
        private UUID projectId;
        private SupportedSystem supportedSystem;

        public TokenEntityId() {
        }

        public TokenEntityId(UUID projectId, SupportedSystem supportedSystem) {
            this.projectId = projectId;
            this.supportedSystem = supportedSystem;
        }
    }

    @Id
    private UUID projectId;

    @Id
    @Enumerated(EnumType.STRING)
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
