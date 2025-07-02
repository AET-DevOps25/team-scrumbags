package com.trace.comms_connector.discord;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class DiscordGuildMember {
    private Map<String, Object> details = new LinkedHashMap<>();

    @JsonAnySetter
    public void setDetail(String key, Object value) {
        this.details.put(key, value);
    }

    @JsonAnyGetter
    public Object getDetail(String key) {
        return this.details.get(key);
    }
}
