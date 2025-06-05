package com.trace.sdlc_connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataRepo extends JpaRepository<DataEntity, UUID> {

    List<DataEntity> findAllByProjectId(UUID projectId);
}
