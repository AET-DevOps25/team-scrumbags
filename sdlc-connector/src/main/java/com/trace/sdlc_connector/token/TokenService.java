package com.trace.sdlc_connector.token;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
public class TokenService {

    private final TokenRepo tokenRepo;

    public TokenService(TokenRepo tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    public TokenEntity saveToken(@NonNull UUID projectId, @NonNull SupportedSystem supportedSystem, @NonNull String token) {
        var tokenEntity = new TokenEntity(token, projectId, supportedSystem);

        // Save the tokenEntity to the database using your repository
        tokenEntity = tokenRepo.save(tokenEntity);
        return tokenEntity;
    }

    public List<TokenEntity> getTokens(@NonNull UUID projectId, @Nullable SupportedSystem supportedSystem) {
        List<TokenEntity> tokens;
        if(supportedSystem != null) {
            tokens = tokenRepo.findAllByProjectIdAndSupportedSystem(projectId, supportedSystem);
        } else {
            tokens = tokenRepo.findAllByProjectId(projectId);
        }
        return tokens;
    }

    public TokenEntity getTokenById(@NonNull UUID tokenId) {
        return tokenRepo.findById(tokenId).orElse(null);
    }

}
