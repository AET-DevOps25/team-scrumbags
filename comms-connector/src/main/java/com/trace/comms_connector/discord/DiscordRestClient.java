package com.trace.comms_connector.discord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.comms_connector.CommsService;
import com.trace.comms_connector.Platform;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class DiscordRestClient {
    @Value("${trace.discord.secret}")
    private String token;

    @Value("${trace.discord.api-version}")
    private String apiVersion;

    @Value("${trace.discord.base-url}")
    private String baseUrl;

    @Autowired
    private CommsService commsService;

    private RestClient getRestClient() {
        return RestClient.builder().baseUrl(baseUrl + "/" + apiVersion).build();
    }

    public List<String> getGuildChannelIds(String guildId) {
        List<DiscordChannel> channels = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/guilds/" + guildId + "/channels")
                .build())
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return channels.stream()
            .filter(channel -> channel.getType() == 0)
            .map(channel -> channel.getId())
            .toList();
    }

    public List<String> getGuildMemberNames(String guildId) {
        List<DiscordUser> users = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/guilds/" + guildId + "/members")
                .build())
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return users.stream()
            .map(user -> user.getUsername())
            .toList();
    }

    /*
     * Returns a string representing a JSON array that contains the messages in a form
     * that is ready to be send to the gen AI microservice
     */
    public String getChannelMessages(String channelId, String lastMessageId, UUID projectId) {
        List<DiscordMessage> messages = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/channels/" + channelId + "/messages")
                .queryParam("limit", 100)
                .queryParamIfPresent("after", Optional.ofNullable(lastMessageId))
                .build())
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        List<String> jsonMessages = messages.stream()
            .map(msg -> {
                UUID userId = commsService.getUserIdByProjectIdAndPlatformDetails(
                    projectId, Platform.DISCORD, msg.getAuthor().getId());
                return msg.getJsonString(userId, projectId);
            })
            .toList();

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(jsonMessages);
        } catch (Exception e) {
            return "{}";
        }
    }
}