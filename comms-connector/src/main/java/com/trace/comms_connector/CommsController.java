package com.trace.comms_connector;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/projects/{projectId}/comms")
@AllArgsConstructor
public class CommsController {
    @Autowired
    private final CommsService commsService;

    /*
     * TODO: Maybe change some path variables to query parameters
     */

    @GetMapping("/{platform}/users")
    public ResponseEntity<?> getPlatformUsers(@PathVariable UUID projectId, @PathVariable Platform platform) {
        var userList = commsService.getUsersByProjectId(projectId, platform);
        
        return ResponseEntity.ok(userList);
    }

    @PostMapping("/{platform}/add")
    public ResponseEntity<?> addCommsIntegration(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @RequestParam(required = true, name = "channelIdList") List<String> channelIdList,
        @RequestParam(required = true, name = "userIdList") List<UUID> userIdList
    ) {
        // TODO: Add integrations without specifying channel and user IDs (get these from Discord and core)
        var connectionList = commsService.addCommIntegration(projectId, platform, channelIdList, userIdList);

        return ResponseEntity.ok(connectionList);
    }

    @PatchMapping("/{platform}/users/save/{userId}/{platformUsername}")
    public ResponseEntity<?> addPlatformUsername(
        @PathVariable UUID projectId,
        @PathVariable Platform platform,
        @PathVariable UUID userId,
        @PathVariable String platformUsername
    ) {
        var userEntity = commsService.saveUser(projectId, userId, platform, platformUsername);

        return ResponseEntity.ok(userEntity);
    }

    @DeleteMapping("/{platform}/delete")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId, @PathVariable Platform platform) {
        commsService.deleteCommIntegration(projectId, platform);

        return ResponseEntity.ok().build();
    }


    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAllCommIntegrations(@PathVariable UUID projectId) {
        commsService.deleteCommIntegration(projectId, null);

        return ResponseEntity.ok().build();
    }
}
