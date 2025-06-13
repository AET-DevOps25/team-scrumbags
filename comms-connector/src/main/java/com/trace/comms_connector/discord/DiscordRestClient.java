package com.trace.comms_connector.discord;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class DiscordRestClient {
    @Value("${trace.discord.secret}")
    private static String token;

    @Value("${trace.discord.api-version}")
    private static String apiVersion;

    @Value("${trace.discord.base-uri}")
    private static String baseUri;

    private static RestClient getRestClient() {
        return RestClient.builder().baseUrl(baseUri + "/" + apiVersion).build();
    }

    public static List<String> getGuildChannelIds(String guildId) {
        List<DiscordChannel> channels = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/guilds/" + guildId + "/channels")
                .build()
            )
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return channels.stream()
            .filter(channel -> channel.getType() == 0)
            .map(channel -> channel.getId())
            .toList();
    }

    public static List<String> getGuildMemberNames(String guildId) {
        List<DiscordUser> users = getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/guilds/" + guildId + "/members")
                .build()
            )
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

        return users.stream()
            .map(user -> user.getUsername())
            .toList();
    }

    public static List<DiscordMessage> getChannelMessages(String channelId, String lastMessageId) {
        return getRestClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/channels/" + channelId + "/messages")
                .queryParam("limit", 100)
                .queryParamIfPresent("after", Optional.ofNullable(lastMessageId))
                .build()
            )
            .header("Authorization", token)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}