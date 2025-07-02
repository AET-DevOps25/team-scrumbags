package com.trace.comms_connector;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.NoArgsConstructor;

@RestController
@RequestMapping("/projects/{projectId}/comms")
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
    @GetMapping("/{platform}/users")
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
    @PostMapping("/{platform}")
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
    @PostMapping("/{platform}/users")
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
    @DeleteMapping("/{platform}")
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
    @DeleteMapping("")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId) {
        commsService.deleteCommsIntegration(projectId, null);

        return ResponseEntity.ok().build();
    }
}
