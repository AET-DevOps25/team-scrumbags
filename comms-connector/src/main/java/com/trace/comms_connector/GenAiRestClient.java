package com.trace.comms_connector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class GenAiRestClient {
    @Value("${trace.genai.base-url}")
    private String baseUrl;

    private RestClient getRestClient() {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Send a list of messages to the gen AI microservice
     * 
     * @param messageJsons is a JSON array of messages, according to the specification in the gen AI endpoint
     * @return reponse entity
     */
    public ResponseEntity<?> sendMessageListToGenAi(String messageJsons) {
        // TODO: Adjust the path and other stuff based on the endpoint in the gen AI service
        return getRestClient()
            .post()
            .uri(uriBuilder -> uriBuilder
                .path("/")
                .build())
            .contentType(MediaType.APPLICATION_JSON)
            .body(messageJsons)
            .retrieve()
            .toBodilessEntity();
    }
}
