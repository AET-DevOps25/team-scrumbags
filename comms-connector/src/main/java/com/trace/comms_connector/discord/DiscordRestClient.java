package com.trace.comms_connector.discord;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.trace.comms_connector.model.CommsPlatformRestClient;

@Component
public class DiscordRestClient implements CommsPlatformRestClient {
    @Value("Bot ${trace.discord.secret}")
    private String token;

    @Value("${trace.discord.bot-id}")
    private String botId;

    @Value("${trace.discord.api-version}")
    private String apiVersion;

    @Value("${trace.discord.base-url}")
    private String baseUrl;

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
        List<DiscordGuildMember> guildMembers = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/guilds/" + guildId + "/members")
                .queryParam("limit", 1000)
                .build())
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return guildMembers.stream()
            .map(member -> member.getUser())
            .filter(user -> !user.getId().equals(botId))
            .map(user -> user.getUsername())
            .toList();
    }

    @Override
    public List<DiscordMessage> getChannelMessages(String channelId, String lastMessageId, UUID projectId) throws RuntimeException {
        List<DiscordMessage> messages = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/channels/" + channelId + "/messages")
                .queryParam("limit", 100)
                .queryParam("after", lastMessageId)
                .build())
            .header("Authorization", token)
            .retrieve()
            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                (request, response) -> {
                    throw new RuntimeException(response.getHeaders().get("Retry-After").getFirst());
                })
            .body(new ParameterizedTypeReference<>() {});

        return messages;
    }
}