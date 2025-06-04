package com.trace.comms_connector.connection;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.trace.comms_connector.Platform;

import io.micrometer.common.lang.Nullable;

@Service
public class ConnectionService {
    @Autowired
    private ConnectionRepo connectionRepo;

    public ConnectionEntity saveConnection(
        @NonNull UUID projectId,
        @NonNull String platformChannelId,
        @NonNull Platform platform,
        @Nullable String lastMessageId
    ) {
        ConnectionEntity connectionEntity = new ConnectionEntity(projectId, platformChannelId, platform, lastMessageId);
        connectionEntity = connectionRepo.save(connectionEntity);
        return connectionEntity;
    }

    /**
     * Get communication integration connections for a project ID (optionally only in given platform)
     * 
     * @param projectId
     * @param platform
     * @return ConnectionEntity list
     */
    public List<ConnectionEntity> getConnections(@NonNull UUID projectId, @Nullable Platform platform) {
        List<ConnectionEntity> connections;
        if (platform != null) {
            connections = connectionRepo.findAllByProjectIdAndPlatform(projectId, platform);
        } else {
            connections = connectionRepo.findAllByProjectId(projectId);
        }
        return connections;
    }

    /**
     * Delete communication integration communications for a project ID (optionally only in given platform)
     * 
     * @param projectId
     * @param platform
     */
    public void deleteConnections(@NonNull UUID projectId, @Nullable Platform platform) {
        if (platform != null) {
            connectionRepo.deleteInBulkByProjectIdAndPlatform(projectId, platform);
        } else {
            connectionRepo.deleteInBulkByProjectID(projectId);
        }
    }
}
