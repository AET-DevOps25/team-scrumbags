package com.trace.sdlc_connector.github.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.message.MessageEntity;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SenderIdTest extends EventTest {

    @Autowired
    private UserMappingRepo userMappingRepo;

    @Test
    void sendMappedUser() throws Exception {
        UUID eventId = UUID.randomUUID();
        String payload = readPayloadFromFile("issueTestPayload.json");

        UUID userId = UUID.randomUUID();
        userMappingRepo.save(new UserMapping(
                this.projectId,
                SupportedSystem.GITHUB,
                "95364200",
                userId
        ));

        this.performEventRequest(eventId, "issues", payload)
                .andExpect(status().isOk());

        // verify that the returned message is equal to what we saved
        MessageEntity savedMessage = messageRepo.findById(eventId).orElseThrow();
        assertThat(savedMessage.getId()).isEqualTo(eventId);
        assertThat(savedMessage.getUserId()).isEqualTo(userId);
    }
}
