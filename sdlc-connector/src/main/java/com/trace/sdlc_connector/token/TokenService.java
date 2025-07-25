package com.trace.sdlc_connector.token;

import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.security.SecurityService;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class TokenService {

    private final TokenRepo tokenRepo;
    private final SecurityService securityService;

    public TokenService(TokenRepo tokenRepo, SecurityService securityService) {
        this.tokenRepo = tokenRepo;
        this.securityService = securityService;
    }

    public TokenEntity saveToken(@NonNull UUID projectId, @NonNull SupportedSystem supportedSystem, @Nullable String token) {
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        TokenEntity tokenEntity = new TokenEntity(token, projectId, supportedSystem);
        if (token == null || token.isBlank()) {
            tokenRepo.deleteById(new TokenEntity.TokenEntityId(projectId, supportedSystem));
        } else {
            tokenEntity = tokenRepo.save(tokenEntity);
        }

        return tokenEntity;
    }

    public List<TokenEntity> getTokens(@NonNull UUID projectId, @Nullable SupportedSystem supportedSystem) {
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        List<TokenEntity> tokens;
        if (supportedSystem != null) {
            tokens = tokenRepo.findAllByProjectIdAndSupportedSystem(projectId, supportedSystem);
        } else {
            tokens = tokenRepo.findAllByProjectId(projectId);
        }
        return tokens;
    }
}
