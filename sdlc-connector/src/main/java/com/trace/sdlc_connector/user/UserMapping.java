package com.trace.sdlc_connector.user;

import com.trace.sdlc_connector.SupportedSystem;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "user_mapping")
@IdClass(UserMapping.UserMappingId.class)
public class UserMapping {
    // primary key over projectId, platform, and userId to allow one platform userId per project
    public static class UserMappingId implements Serializable {
        private UUID projectId;
        private SupportedSystem platform;
        private UUID userId;

        public UserMappingId() {
        }

        public UserMappingId(UUID projectId, SupportedSystem platform, UUID userId) {
            this.projectId = projectId;
            this.platform = platform;
            this.userId = userId;
        }
    }

    @Id
    private UUID projectId;

    @Id
    @Enumerated(EnumType.STRING)
    private SupportedSystem platform;

    @Id
    private UUID userId;

    @Column(nullable = false)
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

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
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
