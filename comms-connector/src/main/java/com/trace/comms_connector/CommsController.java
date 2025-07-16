package com.trace.comms_connector;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import lombok.NoArgsConstructor;

@RestController
@NoArgsConstructor
public class CommsController {
    @Autowired
    private CommsService commsService;

    /**
     * Get the platform users for a given project ID and platform
     * 
     * @param projectId
     * @param platform
     * @return list of user entities
     */
    @Operation(
        summary = "Get the users for a given platform connection",
        description = "Returns a list of platform user IDs / usernames for a given project ID and given platform."
    )
    @GetMapping("/projects/{projectId}/comms/{platform}/users")
    public ResponseEntity<?> getPlatformUsers(@PathVariable UUID projectId, @PathVariable Platform platform) {
        var userList = commsService.getUsersByProjectId(projectId, platform);
        
        return ResponseEntity.ok(userList);
    }

    /**
     * Add a comms integration, e.g. for Discord it adds every text channel in the server
     * 
     * @param projectId
     * @param platform
     * @param serverId
     * @return list of newly added connections
     */
    @Operation(
        summary = "Add a communication integration and the users in the platform",
        description = "Adds the given communication platform to the table of connections for this project ID. " + 
            "For Discord, the server ID corresponds to the Discord server ID, also known as the guild ID. " + 
            "All of the users in the platform are also saved into the users table, except for the Trace bot, e.g. in Discord."
    )
    @PostMapping("/projects/{projectId}/comms/{platform}")
    public ResponseEntity<?> addCommsIntegration(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = false) String serverId
    ) {
        if (serverId == null) {
            return ResponseEntity.badRequest().body("Communication platorm server ID must be specified!"); 
        }

        try {
            var connectionList = commsService.addCommsIntegration(projectId, platform, serverId);
            return ResponseEntity.ok(connectionList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /**
     * Add a platform user, can be used to update the trace ID for a give platform user ID
     * 
     * @param projectId
     * @param platform
     * @param userId
     * @param platformUserId
     * @return new or updated user entity
     */
    @Operation(
        summary = "Add a platform user",
        description = "Saves the specified user in the database. For an existing combination of project ID, platform " +
            " and platform user ID, this endpoint can be used to assign a Trace UUID by overwriting the existing entry."
    )
    @PostMapping("/projects/{projectId}/comms/{platform}/users")
    public ResponseEntity<?> addPlatformUser(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String platformUserId
    ) {
        if (userId == null || platformUserId == null) {
            return ResponseEntity.badRequest().body("User ID and platform username must be specified!");
        }

        var userEntity = commsService.saveUser(projectId, platformUserId, platform, userId);

        return ResponseEntity.ok(userEntity);
    }

    /**
     * Delete every comms integration on a given platform for a given project ID
     * 
     * @param projectId
     * @param platform
     * @return
     */
    @Operation(
        summary = "Delete comms integrations for platform",
        description = "Deletes the communications integrations for the given project ID and the given platform specifically."
    )
    @DeleteMapping("/projects/{projectId}/comms/{platform}")
    public ResponseEntity<?> deletePlatformCommIntegrations(@PathVariable UUID projectId, @PathVariable Platform platform) {
        commsService.deleteCommsIntegration(projectId, platform);

        return ResponseEntity.ok().build();
    }

    /**
     * Delete every comms integration for the given project ID
     * 
     * @param projectId
     * @return
     */
    @Operation(
        summary = "Delete comms integrations",
        description = "Deletes all communications integrations for the given project ID."
    )
    @DeleteMapping("/projects/{projectId}/comms")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId) {
        commsService.deleteCommsIntegration(projectId, null);

        return ResponseEntity.ok().build();
    }

    /**
     * Get a message batch from Discord text channel, for testing
     * 
     * @param projectId
     * @param platform
     * @param channelId
     * @param lastMessageId
     * @return String in the format of a JSON array of objects with the format of the gen AI microservice
     */
    @Operation(
        summary = "Get Discord messages from channel",
        description = "Returns a list of a batch of 100 platform messages from the specified channel ID (for Discord: the ID of a text" +
            " channel that was added to the connections database using add integration endpoint prior to this). The messages are formatted" +
            " according to the JSON format specified by the gen AI microservice. Last message ID can be specified to only get the" +
            " messages after a specific message with the given ID. If lastMessageId is not specified, the last message ID is fetched" +
            " instead. Setting updateLastMessageId to true will update the last message ID in the connections table," +
            " and setting sendToGenAi to true will send the messages to the gen AI microservice."
    )
    @GetMapping("/projects/{projectId}/comms/{platform}/messages")
    public ResponseEntity<?> getMessagesFromChannel(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = false) String channelId,
        @RequestParam(required = false) String lastMessageId,
        @RequestParam(required = false, defaultValue = "false") boolean updateLastMessageId,
        @RequestParam(required = false, defaultValue = "false") boolean sendToGenAi
    ) {
        if (channelId == null) {
            return ResponseEntity.badRequest().body("Communication channel ID must be specified!"); 
        }

        String messageJsonList = commsService.getMessageBatchFromChannel(
            projectId, platform, channelId, lastMessageId, updateLastMessageId, sendToGenAi);

        return ResponseEntity.ok(messageJsonList);
    }
}
