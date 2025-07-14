package com.trace.sdlc_connector.github.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.config.MockKeycloakConfig;
import com.trace.sdlc_connector.message.MessageEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommitCommentTest extends EventTest{

    private static final Logger logger = LoggerFactory.getLogger(CommitCommentTest.class);

    @Test
    void sendCommitCommentEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload ="""
                {
                    "action": "created",
                    "comment": {
                        "id": 123456,
                        "body": "This is a test comment",
                        "created_at": "2023-10-01T12:00:00Z"
                    },
                    "sender": {
                        "id": 78910,
                        "login": "testuser"
                    }
                }
                """;

        this.performEventRequest(eventId, "commit_comment", payload)
                .andExpect(status().isOk());

        // verify that the returned message is equal to what we saved
        MessageEntity savedMessage = messageRepo.findById(eventId).orElseThrow();
        logger.info(savedMessage.getContent().toString());
        assertThat(savedMessage.getId()).isEqualTo(eventId);
        assertThat(savedMessage.getType()).isEqualTo("commit_comment created");
        var payloadMap = new ObjectMapper().readValue(payload, Map.class);
        assertThat(((Map)savedMessage.getContent().get("sender")).get("id")).isEqualTo(((Map)payloadMap.get("sender")).get("id"));
        assertThat(((Map)savedMessage.getContent().get("comment")).get("id")).isEqualTo(((Map)payloadMap.get("comment")).get("id"));
    }
}
