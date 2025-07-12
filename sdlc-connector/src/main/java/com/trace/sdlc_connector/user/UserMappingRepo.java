package com.trace.sdlc_connector.user;

import com.trace.sdlc_connector.SupportedSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserMappingRepo extends JpaRepository<UserMapping, UserMapping.UserMappingId> {
    List<UserMapping> findAllByProjectId(UUID projectId);
    List<UserMapping> findAllByProjectIdAndPlatform(UUID projectId, SupportedSystem platform);
    List<UserMapping> findAllByProjectIdAndPlatformAndPlatformUserId(UUID projectId, SupportedSystem platform, String platformUserId);
}
