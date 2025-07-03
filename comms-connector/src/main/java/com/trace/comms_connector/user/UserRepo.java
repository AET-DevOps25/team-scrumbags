package com.trace.comms_connector.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trace.comms_connector.Platform;

@Repository
public interface UserRepo extends JpaRepository<UserEntity, UserCompositeKey> {
    
    List<UserEntity> findAllByProjectId(UUID projectId);

    List<UserEntity> findAllByProjectIdAndUserId(UUID projectId, UUID userId);

    List<UserEntity> findAllByProjectIdAndPlatform(UUID projectId, Platform platform);

    void deleteInBulkByProjectId(UUID projectId);
    
    void deleteInBulkByUserId(UUID userId);
    
    void deleteInBulkByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteInBulkByProjectIdAndPlatform(UUID projectId, Platform platform);
}
