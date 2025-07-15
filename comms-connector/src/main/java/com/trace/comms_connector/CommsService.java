package com.trace.comms_connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.comms_connector.connection.ConnectionEntity;
import com.trace.comms_connector.connection.ConnectionRepo;
import com.trace.comms_connector.discord.DiscordMessage;
import com.trace.comms_connector.discord.DiscordRestClient;
import com.trace.comms_connector.user.UserCompositeKey;
import com.trace.comms_connector.user.UserEntity;
import com.trace.comms_connector.user.UserRepo;

import lombok.NoArgsConstructor;

@Service
@NoArgsConstructor
public class CommsService {
    @Autowired
    private ConnectionRepo connectionRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private DiscordRestClient discordClient;

    @Autowired
    private CommsRestClient commsClient;

    // Save connection to the connection database
    @Transactional
    public ConnectionEntity saveConnection(
        @NonNull UUID projectId,
        @NonNull String platformChannelId,
        @NonNull Platform platform,
        @Nullable String lastMessageId
    ) {
        if (lastMessageId == null) {
            lastMessageId = "0"; // Default to 0 if no last message ID is provided
        }
        
        ConnectionEntity connectionEntity = new ConnectionEntity(projectId, platformChannelId, platform, lastMessageId);
        connectionEntity = connectionRepo.save(connectionEntity);
        return connectionEntity;
    }

    // Get communication integration connections for a project ID (optionally only in given platform)
    public List<ConnectionEntity> getConnections(@NonNull UUID projectId, @Nullable Platform platform) {
        List<ConnectionEntity> connections;
        if (platform != null) {
            connections = connectionRepo.findAllByProjectIdAndPlatform(projectId, platform);
        } else {
            connections = connectionRepo.findAllByProjectId(projectId);
        }
        return connections;
    }

    // Delete communication integration communications for a project ID (optionally only in given platform)     
    @Transactional
    public void deleteConnections(@NonNull UUID projectId, @Nullable Platform platform) {
        if (platform != null) {
            connectionRepo.deleteInBulkByProjectIdAndPlatform(projectId, platform);
        } else {
            connectionRepo.deleteInBulkByProjectId(projectId);
        }
    }

    // Save user to the user database, is also used when adding userId to a platformUserId
    @Transactional
    public UserEntity saveUser(
        @NonNull UUID projectId,
        @NonNull String platformUserId,
        @NonNull Platform platform,
        @Nullable UUID userId
    ) {
        UserEntity userEntity = new UserEntity(projectId, platformUserId, platform, userId);
        userEntity = userRepo.save(userEntity);
        return userEntity;
    }

    // Get communication integration user entries of a project (optionally only in given platform)
    public List<UserEntity> getUsersByProjectId(@NonNull UUID projectId, @Nullable Platform platform) {
        List<UserEntity> users;
        if (platform != null) {
            users = userRepo.findAllByProjectIdAndPlatform(projectId, platform);
        } else {
            users = userRepo.findAllByProjectId(projectId);
        }
        return users;
    }

    // Delete all communication integration user entries of a project (optionally only in given platform)
    @Transactional
    public void deleteUsersByProjectId(@NonNull UUID projectId, @Nullable Platform platform) {
        if (platform != null) {
            userRepo.deleteInBulkByProjectIdAndPlatform(projectId, platform);
        } else {
            userRepo.deleteInBulkByProjectId(projectId);
        }
    }

    // Delete communication integration user entries for a given user ID (optionally only in given project ID)
    @Transactional
    public void deleteUsersByUserId(@NonNull UUID userId, @Nullable UUID projectId) {
        if (projectId != null) {
            userRepo.deleteInBulkByProjectIdAndUserId(projectId, userId);
        } else {
            userRepo.deleteInBulkByUserId(userId);
        }
    }

    // Delete communication integrations for a given project (optionally only in given platform)
    @Transactional
    public void deleteCommsIntegration(@NonNull UUID projectId, @Nullable Platform platform) {
        this.deleteConnections(projectId, platform);
        this.deleteUsersByProjectId(projectId, platform);
    }

    // Add a communication integration to a project by saving the corresponding channel and user IDs in the repos
    @Transactional
    public List<ConnectionEntity> addCommsIntegration(
        @NonNull UUID projectId,
        @NonNull Platform platform,
        @NonNull String serverId
    ) throws Exception {
        ArrayList<ConnectionEntity> connections = new ArrayList<ConnectionEntity>(); 
        List<String> channelIdList, platformUserIdList;

        if (platform.equals(Platform.DISCORD)) {
            channelIdList = discordClient.getGuildChannelIds(serverId);
            platformUserIdList = discordClient.getGuildMemberNames(serverId);
        } else {
            throw new Exception("Platform not supported.");
        }

        for (String channel : channelIdList) {
            var connection = this.saveConnection(projectId, channel, platform, null); 
            connections.add(connection);
        }
        for (String platformUser : platformUserIdList) {
            this.saveUser(projectId, platformUser, platform, null);
        }
        return connections;
    }

    // Get all connections, used for the thread that periodically pulls messages
    public List<ConnectionEntity> getAllConnections() {
        return connectionRepo.findAll();
    }

    // Get the trace user ID for a user in the communication channel, used while creating
    // the JSON to send to the gen AI microservice
    public UUID getUserIdByProjectIdAndPlatformDetails(UUID projectId, Platform platform, String platformUserId) {
        Optional<UserEntity> user = userRepo.findById(new UserCompositeKey(projectId, platformUserId, platform));
        return user.isPresent() ? user.get().getUserId() : null;
    }

    // Used for testing getting the messages from a Discord channel
    public String getAllMessagesFromChannel(UUID projectId, Platform platform, String channelId) {
        List<DiscordMessage> messageBatch = new ArrayList<>();
        List<DiscordMessage> allMessages = new ArrayList<>();
        
        String lastMessageId = "0"; // Start from the beginning

        do {
            messageBatch = discordClient.getChannelMessages(
                channelId,
                lastMessageId,
                projectId);

            saveConnection(projectId, channelId, platform, messageBatch.get(0).getId());

            allMessages.addAll(messageBatch);
        } while (!messageBatch.isEmpty());

        if (allMessages.isEmpty()) {
            return "[]"; // Return empty JSON array if no messages found
        }
        
        // Convert to a list of JSON strings
        List<String> jsonMessages = allMessages.stream()
            .map(msg -> {
                UUID userId = getUserIdByProjectIdAndPlatformDetails(
                    projectId, platform, msg.getAuthor().getIdentifier());
                return msg.getJsonString(userId, projectId);
            })
            .toList();

        String messageJsonArray = "";
        
        // Convert to a single string of a JSON array
        ObjectMapper mapper = new ObjectMapper();
        try {
            messageJsonArray = mapper.writeValueAsString(jsonMessages);
        } catch (Exception e) { return "";}

        // Send to the gen AI microservice
        commsClient.sendMessageListToGenAi(messageJsonArray);

        return messageJsonArray;
    }
}