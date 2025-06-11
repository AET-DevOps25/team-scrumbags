package com.trace.comms_connector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.trace.comms_connector.connection.ConnectionEntity;
import com.trace.comms_connector.connection.ConnectionRepo;
import com.trace.comms_connector.user.UserEntity;
import com.trace.comms_connector.user.UserRepo;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CommsService {
    @Autowired
    private final ConnectionRepo connectionRepo;

    @Autowired
    private final UserRepo userRepo;

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
    public UserEntity saveUser(
        @NonNull UUID projectId,
        @NonNull UUID userId,
        @NonNull Platform platform,
        @Nullable String platformUsername
    ) {
        UserEntity userEntity = new UserEntity(projectId, userId, platform, platformUsername);
        userEntity = userRepo.save(userEntity);
        return userEntity;
    }

    /**
     * Get communication integration user entries of a project (optionally only in given platform)
     * 
     * @param projectId
     * @param platform
     * @return UserEntity list
     */
    public List<UserEntity> getUsersByProjectId(@NonNull UUID projectId, @Nullable Platform platform) {
        List<UserEntity> users;
        if (platform != null) {
            users = userRepo.findAllByProjectIdAndPlatform(projectId, platform);
        } else {
            users = userRepo.findAllByProjectId(projectId);
        }
        return users;
    }

    /**
     * Delete all communication integration user entries of a project (optionally only in given platform)
     * 
     * @param projectId
     * @param platform
     */
    public void deleteUsersByProjectId(@NonNull UUID projectId, @Nullable Platform platform) {
        if (platform != null) {
            userRepo.deleteInBulkByProjectIdAndPlatform(projectId, platform);
        } else {
            userRepo.deleteInBulkByProjectId(projectId);
        }
    }

    /**
     * Delete communication integration user entries for a given user ID (optionally only in given project ID)
     * 
     * @param userId
     * @param projectId
     */
    public void deleteUsersByUserId(@NonNull UUID userId, @Nullable UUID projectId) {
        if (projectId != null) {
            userRepo.deleteInBulkByProjectIdAndUserId(projectId, userId);
        } else {
            userRepo.deleteInBulkByUserId(userId);
        }
    }

    /**
     * Delete communication integrations for a given project (optionally only in given platform)
     * 
     * @param projectId
     * @param platform
     */
    public void deleteCommIntegration(@NonNull UUID projectId, @Nullable Platform platform) {
        this.deleteConnections(projectId, platform);
        this.deleteUsersByProjectId(projectId, platform);
    }

    /**
     * Add a communication integration to a project by saving the corresponding channel and user IDs in the repos
     * 
     * @param projectId
     * @param platform
     * @param channelIdList
     * @param userIdList
     * @return
     */
    public List<ConnectionEntity> addCommIntegration(
        @NonNull UUID projectId,
        @NonNull Platform platform,
        @NonNull List<String> channelIdList,
        @NonNull List<UUID> userIdList
    ) {
        // TODO: Add integrations without specifying channel and user IDs (get these from Discord and core)
        ArrayList<ConnectionEntity> connections = new ArrayList<ConnectionEntity>(); 
        for (String channel : channelIdList) {
            var connection = this.saveConnection(projectId, channel, platform, null); 
            connections.add(connection);
        }
        for (UUID user : userIdList) {
            this.saveUser(projectId, user, platform, null);
        }
        return connections;
    }
}