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

    public TokenEntity saveToken(@NonNull UUID projectId, @NonNull SupportedSystem supportedSystem, @NonNull String token) {
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        var tokenEntity = new TokenEntity(token, projectId, supportedSystem);

        // Save the tokenEntity to the database using your repository
        tokenEntity = tokenRepo.save(tokenEntity);
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

    public TokenEntity getTokenById(@NonNull UUID projectId, @NonNull UUID tokenId) {
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        var token = tokenRepo.findById(tokenId).orElse(null);
        if (token != null && !token.getProjectId().equals(projectId)) {
            throw new SecurityException("Token with ID: " + tokenId + " does not belong to project with ID: " + projectId);
        }

        return token;
    }

}
