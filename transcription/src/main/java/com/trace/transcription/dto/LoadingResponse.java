package com.trace.transcription.dto;

import java.util.UUID;

public class LoadingResponse {
    private UUID transcriptId;
    private boolean loading;

    public LoadingResponse(UUID transcriptId, boolean loading) {
        this.transcriptId = transcriptId;
        this.loading = loading;
    }

    public UUID getTranscriptId() {
        return transcriptId;
    }

    public boolean getLoading() {
        return loading;
    }
}