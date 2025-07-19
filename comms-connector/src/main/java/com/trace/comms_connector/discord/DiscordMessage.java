package com.trace.comms_connector.discord;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.trace.comms_connector.Platform;
import com.trace.comms_connector.model.CommsMessage;
import com.trace.comms_connector.model.GenAiMessage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class DiscordMessage extends CommsMessage {
    private String id;
    private String channel_id;
    private DiscordUser author;
    private String content;
    private String timestamp;
    private Map<String, Object> details = new LinkedHashMap<>();

    @JsonAnySetter
    public void setDetail(String key, Object value) {
        this.details.put(key, value);
    }

    @JsonAnyGetter
    public Object getDetail(String key) {
        return this.details.get(key);
    }

    public GenAiMessage getGenAiMessage(UUID userId, UUID projectId) throws NullPointerException {
        if (projectId == null) {
            throw new NullPointerException("Project ID cannot be null!");
        } 

        return new GenAiMessage(
            "communicationMessage",
            (userId == null ? null : userId),
            Instant.parse(this.getTimestamp()).getEpochSecond(),
            projectId,
            Platform.DISCORD,
            this.getContent(),
            this.getAuthor().getUsername(),
            this.getAuthor().getGlobal_name()
        );
    }
}
