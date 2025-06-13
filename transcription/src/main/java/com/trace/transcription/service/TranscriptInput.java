package com.trace.transcription.service;

import java.util.UUID;

public class TranscriptInput {
    public Metadata metadata;
    public Content content;

    public static class Metadata {
        public String type;
        public String user;
        public long timestamp;
        public UUID project_id;
    }

    public static class Content {
        public String index;
        public String start;
        public String end;
        public String text;
        public String speaker;
        public String speaker_id;
    }
}
