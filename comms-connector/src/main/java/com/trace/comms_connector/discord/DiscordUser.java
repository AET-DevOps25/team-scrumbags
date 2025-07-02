package com.trace.comms_connector.discord;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.trace.comms_connector.model.CommsPlatformUser;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor @Getter @Setter
public class DiscordUser implements CommsPlatformUser {
    private String id;
    private String username;
    private String discriminator;
    private String global_name;
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
