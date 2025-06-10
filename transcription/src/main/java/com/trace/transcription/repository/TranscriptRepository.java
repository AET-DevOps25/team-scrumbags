package com.trace.transcription.repository;

import com.trace.transcription.model.TranscriptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<TranscriptEntity, UUID> {
    List<TranscriptEntity> findAllByProjectId(UUID projectId);
}
