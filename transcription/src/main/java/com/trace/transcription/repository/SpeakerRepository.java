package com.trace.transcription.repository;

import com.trace.transcription.model.SpeakerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeakerRepository extends JpaRepository<SpeakerEntity, String> {
}
