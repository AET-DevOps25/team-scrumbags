package com.trace.comms_connector.discord;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trace.comms_connector.model.CommsMessage;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class DiscordMessage implements CommsMessage {
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
    public void getDetail(String key) {
        this.details.get(key);
    }

    public String getJsonString(UUID userId, UUID projectId) throws NullPointerException {
        if (projectId == null) {
            throw new NullPointerException("Project ID cannot be null!");
        }

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode root = mapper.createObjectNode();
        ObjectNode metadata = mapper.createObjectNode();
        ObjectNode content = mapper.createObjectNode();

        metadata.put("type", "communicationMessage");
        metadata.put("user", (userId == null ? null : userId.toString()));
        metadata.put("timestamp", Instant.parse(this.getTimestamp()).getEpochSecond());
        metadata.put("projectId", projectId.toString());

        content.put("platform", "discord");
        content.put("message", this.getContent());
        content.put("platformUserId", this.getAuthor().getGlobal_name());

        root.set("metadata", metadata);
        root.set("content", content);

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }
}
