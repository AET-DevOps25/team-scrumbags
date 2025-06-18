package com.trace.comms_connector;

import java.util.List;
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

    @GetMapping("/{platform}/users")
    public ResponseEntity<?> getPlatformUsers(@PathVariable UUID projectId, @PathVariable Platform platform) {
        var userList = commsService.getUsersByProjectId(projectId, platform);
        
        return ResponseEntity.ok(userList);
    }

    @PostMapping("/{platform}")
    public ResponseEntity<?> addCommsIntegration(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = false) String serverId,
        @RequestParam(required = true) List<UUID> userIdList
    ) {
        // TODO: maybe get user IDs from server
        if (serverId == null) {
            return ResponseEntity.badRequest().body("Communication platorm server ID must be specified!"); 
        }

        try {
            var connectionList = commsService.addCommIntegration(projectId, platform, serverId, userIdList);
            return ResponseEntity.ok(connectionList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/{platform}/users/")
    public ResponseEntity<?> addPlatformUser(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String platformUserId
    ) {
        if (userId == null || platformUserId == null) {
            return ResponseEntity.badRequest().body("User ID and platform username must be specified!");
        }

        var userEntity = commsService.saveUser(projectId, userId, platform, platformUserId);

        return ResponseEntity.ok(userEntity);
    }

    @DeleteMapping("/{platform}")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId, @PathVariable Platform platform) {
        commsService.deleteCommIntegration(projectId, platform);

        return ResponseEntity.ok().build();
    }


    @DeleteMapping("")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId) {
        commsService.deleteCommIntegration(projectId, null);

        return ResponseEntity.ok().build();
    }
}
