package com.trace.transcription.dto;

import java.util.UUID;

public class TranscriptInput {
    public Metadata metadata;
    public Content content;

    public static class Metadata {
        public String type;
        public String user;
        public long timestamp;
        public UUID projectId;
    }

    public static class Content {
        public String index;
        public String start;
        public String end;
        public String text;
        public String userName;
        public String userId;
    }
}
