package com.trace.sdlc_connector.message.persist;

import com.trace.sdlc_connector.message.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepo extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findAllByProjectId(UUID projectId);
}
