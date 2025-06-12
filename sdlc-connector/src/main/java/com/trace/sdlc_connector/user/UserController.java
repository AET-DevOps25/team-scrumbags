package com.trace.sdlc_connector.user;

import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {


    private final UserMappingRepo userMappingRepo;

    public UserController(UserMappingRepo userMappingRepo) {
        this.userMappingRepo = userMappingRepo;
    }

    @PostMapping("projects/{projectId}/users")
    public ResponseEntity<?> saveUserMapping(@PathVariable UUID projectId,
                                             @RequestBody UserMapping userMapping
    ) {
        if (userMapping.getPlatform() == null || !StringUtils.hasText(userMapping.getPlatformUserId()) || userMapping.getUserId() == null) {
            return ResponseEntity.badRequest().body("Invalid user mapping data. Platform, platformUserId, and userId are required.");
        }

        userMapping.setProjectId(projectId);
        userMapping = userMappingRepo.save(userMapping);

        return ResponseEntity.ok(userMapping);
    }

    @GetMapping("projects/{projectId}/users")
    public ResponseEntity<?> getUserMapping(@PathVariable UUID projectId,
                                            @RequestParam(required = false) SupportedSystem platform,
                                            @RequestParam(required = false) String platformUserId
                                            ) {

        List<UserMapping> userMappings;
        if(platform != null){
            if(StringUtils.hasText(platformUserId)) {
                userMappings = userMappingRepo.findById(new UserMapping.UserMappingId(projectId, platform, platformUserId))
                        .stream().toList();
            } else {
                userMappings = userMappingRepo.findAllByProjectIdAndPlatform(projectId, platform);
            }
        } else {
            userMappings = userMappingRepo.findAllByProjectId(projectId);
        }

        return ResponseEntity.ok(userMappings);
    }
}
