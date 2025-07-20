package com.trace.sdlc_connector.message.forward;

import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.message.MessageDTO;
import com.trace.sdlc_connector.message.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@Profile("forward")
public class MessageForward extends MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageForward.class);

    private final RestClient restClient;

    @Value("${trace.gen-ai.url}")
    private String genAiUrl;

    public MessageForward() {
        super();
        this.restClient = RestClient.create();
    }

    public void processMessage(UUID projectId, Message message) {
        try {
            String response = this.restClient.post()
                    .uri(genAiUrl + "/content")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MessageDTO[]{new MessageDTO(message)})
                    .retrieve()
                    .toEntity(String.class)
                    .getBody();

            logger.info("Message forwarded successfully: {}", message.getMetadata().getEventId());
        } catch (Exception e) {
            logger.error("Error forwarding message: {}", message.getMetadata().getEventId(), e);
        }
    }
}
