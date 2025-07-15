package com.trace.sdlc_connector.token;

import com.trace.sdlc_connector.SupportedSystem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class TokenController {

    private final TokenService service;

    public TokenController(TokenService service) {
        this.service = service;
    }

    @PostMapping("projects/{projectId}/token")
    public ResponseEntity<?> savePlatformToken(@PathVariable UUID projectId, @RequestParam(required = false, defaultValue = "GITHUB", name = "system") SupportedSystem supportedSystem, @RequestBody String token) {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token is missing or empty");
        }

        var tokenEntity = service.saveToken(projectId, supportedSystem, token);

        return ResponseEntity.ok(tokenEntity);
    }

    @GetMapping("projects/{projectId}/token")
    public ResponseEntity<?> getTokens(@PathVariable UUID projectId, @RequestParam(required = false) SupportedSystem supportedSystem) {
        var tokens = service.getTokens(projectId, supportedSystem);

        return ResponseEntity.ok(tokens);
    }

    @GetMapping("projects/{projectId}/token/{tokenId}")
    public ResponseEntity<?> getToken(@PathVariable UUID projectId, @PathVariable UUID tokenId) {
        TokenEntity token = service.getTokenById(projectId, tokenId);
        if (token == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(token);
    }
}
