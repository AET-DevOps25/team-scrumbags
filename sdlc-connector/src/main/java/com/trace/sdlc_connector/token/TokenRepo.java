package com.trace.sdlc_connector.token;

import com.trace.sdlc_connector.SupportedSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TokenRepo extends JpaRepository<TokenEntity, UUID> {

    List<TokenEntity> findAllByProjectId(UUID projectId);

    List<TokenEntity> findAllByProjectIdAndSupportedSystem(UUID projectId, SupportedSystem supportedSystem);
}
