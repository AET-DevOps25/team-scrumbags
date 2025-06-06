package com.trace.sdlc_connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepo extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findAllByProjectId(UUID projectId);
}
