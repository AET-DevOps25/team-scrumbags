package com.trace.comms_connector.connection;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trace.comms_connector.Platform;

@Repository
public interface ConnectionRepo extends JpaRepository<ConnectionEntity, ConnectionCompositeKey> {

    List<ConnectionEntity> findAllByProjectId(UUID projectId);

    List<ConnectionEntity> findAllByProjectIdAndPlatform(UUID projectId, Platform platform);

    void deleteInBulkByProjectId(UUID projectId);

    void deleteInBulkByProjectIdAndPlatform(UUID projectId, Platform platform);
}
