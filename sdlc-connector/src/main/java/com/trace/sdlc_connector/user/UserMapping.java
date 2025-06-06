package com.trace.sdlc_connector.user;

import com.trace.sdlc_connector.SupportedSystem;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "user_mapping")
@IdClass(UserMapping.UserMappingId.class)
public class UserMapping {
    // primary key over projectId, platform, and platformUserId to have multiple platform user associated to more than one project user
    public static class UserMappingId implements Serializable {
        private UUID projectId;
        private SupportedSystem platform;
        private String platformUserId;

        public UserMappingId() {
        }

        public UserMappingId(UUID projectId, SupportedSystem platform, String platformUserId) {
            this.projectId = projectId;
            this.platform = platform;
            this.platformUserId = platformUserId;
        }
    }

    @Id
    private UUID projectId;

    @Id
    @Enumerated(EnumType.STRING)
    private SupportedSystem platform;

    @Column(nullable = false)
    private UUID userId;

    @Id
    private String platformUserId;

    public UserMapping() {
    }

    public UserMapping(UUID projectId, SupportedSystem platform, String platformUserId, UUID userId) {
        this.projectId = projectId;
        this.platform = platform;
        this.platformUserId = platformUserId;
        this.userId = userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public SupportedSystem getPlatform() {
        return platform;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public UUID getUserId() {
        return userId;
    }
}
