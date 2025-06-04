package com.trace.comms_connector.user;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.trace.comms_connector.Platform;

@Service
public class UserService {
    @Autowired
    private UserRepo userRepo;

    public UserEntity saveUser(
        @NonNull UUID projectId,
        @NonNull UUID userId,
        @NonNull Platform platform,
        @Nullable String platformUserId
    ) {
        UserEntity userEntity = new UserEntity(projectId, userId, platform, platformUserId);
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
}
