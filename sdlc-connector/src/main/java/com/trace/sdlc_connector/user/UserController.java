package com.trace.sdlc_connector.user;

import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class UserController {


    private final UserMappingRepo userMappingRepo;

    public UserController(UserMappingRepo userMappingRepo) {
        this.userMappingRepo = userMappingRepo;
    }

    @PostMapping("projects/{projectId}/users/{platform}/{platformUserId}")
    public ResponseEntity<?> saveUserMapping(@PathVariable UUID projectId,
                                             @PathVariable SupportedSystem platform,
                                             @PathVariable String platformUserId,
                                             @RequestParam(required = true, name = "userId") UUID userId
    ) {
        if (platformUserId.isEmpty()) {
            return ResponseEntity.badRequest().body("platform user id is empty");
        }

        var userMapping = userMappingRepo.save(new UserMapping(
                projectId,
                platform,
                platformUserId,
                userId
        ));


        return ResponseEntity.ok(userMapping);
    }

    @GetMapping("projects/{projectId}/users")
    public ResponseEntity<?> getUserMappingByPlatform(@PathVariable UUID projectId) {

        var userMappings = userMappingRepo.findAllByProjectId(projectId);

        return ResponseEntity.ok(userMappings);
    }

    @GetMapping("projects/{projectId}/users/{platform}")
    public ResponseEntity<?> getUserMappingByPlatform(@PathVariable UUID projectId,
                                                      @PathVariable SupportedSystem platform) {

        var userMappings = userMappingRepo.findAllByProjectIdAndPlatform(projectId, platform);

        return ResponseEntity.ok(userMappings);
    }
}
