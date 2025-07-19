package com.trace.sdlc_connector.github.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.message.persist.MessageEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IssueTest extends EventTest {

    private static final Logger logger = LoggerFactory.getLogger(IssueTest.class);

    @Test
    void sendIssueEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = readPayloadFromFile("issueTestPayload.json");

        this.performEventRequest(eventId, "issues", payload)
                .andExpect(status().isOk());

        // verify that the returned message is equal to what we saved
        MessageEntity savedMessage = messageRepo.findById(eventId).orElseThrow();
        logger.info("savedMessageEntity: {}", savedMessage.getContent().toString());
        assertThat(savedMessage.getId()).isEqualTo(eventId);
        assertThat(savedMessage.getType()).isEqualTo("issues unassigned");

        var payloadMap = new ObjectMapper().readValue(payload, Map.class);
        assertThat(((Map)savedMessage.getContent().get("sender")).get("id")).isEqualTo(((Map)payloadMap.get("sender")).get("id"));

        assertThat(((Map)savedMessage.getContent().get("issue")).get("id")).isEqualTo(((Map)payloadMap.get("issue")).get("id"));

    }
}
