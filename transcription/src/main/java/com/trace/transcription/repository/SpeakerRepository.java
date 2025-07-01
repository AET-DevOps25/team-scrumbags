package com.trace.transcription.repository;

import com.trace.transcription.model.SpeakerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpeakerRepository extends JpaRepository<SpeakerEntity, String> {
    List<SpeakerEntity> findAllByProjectId(UUID projectId);
    SpeakerEntity findByProjectIdAndId(UUID projectId, String Id);
}
