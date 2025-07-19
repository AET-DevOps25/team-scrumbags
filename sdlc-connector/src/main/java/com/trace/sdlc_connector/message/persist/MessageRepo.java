package com.trace.sdlc_connector.message.persist;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Profile("persist")
public interface MessageRepo extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findAllByProjectId(UUID projectId);
}
