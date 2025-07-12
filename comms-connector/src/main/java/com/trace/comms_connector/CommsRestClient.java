package com.trace.comms_connector;

import java.util.HashSet;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class CommsRestClient {
    @Value("${trace.genai.base-url}")
    private String genAiBaseUrl;

    @Value("${trace.project-management.base-url}")
    private String projectManagementBaseUrl;

    private RestClient getGenAiClient() {
        return RestClient.builder().baseUrl(genAiBaseUrl).build();
    }

    private RestClient getProjectManagementClient() {
        return RestClient.builder().baseUrl(projectManagementBaseUrl).build();
    }

    /**
     * Send a list of messages to the gen AI microservice
     * 
     * @param messageJsons is a JSON array of messages, according to the specification in the gen AI endpoint
     * @return reponse entity
     */
    public ResponseEntity<?> sendMessageListToGenAi(String messageJsons) {
        return getGenAiClient()
            .post()
            .uri(uriBuilder -> uriBuilder
                .path("/content")
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .body(messageJsons)
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * Get the user IDs in a project with given ID from the project management microservice
     * 
     * @param projectId
     * @return
     */
    ResponseEntity<HashSet<UUID>> getUsersInProject(UUID projectId) {
        return getProjectManagementClient()
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/projects/" + projectId.toString() + "/users")
                .build())
            .retrieve()
            .toEntity(new ParameterizedTypeReference<HashSet<UUID>>() {});
    }
}
