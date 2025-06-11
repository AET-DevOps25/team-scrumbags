package com.trace.comms_connector.discord;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class DiscordMessage {
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
}
