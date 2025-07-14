package com.trace.sdlc_connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.config.MockKeycloakConfig;
import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.message.MessageEntity;
import com.trace.sdlc_connector.message.Metadata;
import com.trace.sdlc_connector.message.persist.MessageRepo;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenRepo;
import com.trace.sdlc_connector.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MockKeycloakConfig.class)
class MessageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessageRepo messageRepo;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void getAllMessages() throws Exception {
        UUID projectId = UUID.randomUUID();
        MessageEntity savedMessage = new MessageEntity(UUID.randomUUID(), "test-type", UUID.randomUUID(), new Date(), projectId,
                Map.of("contentKey", "contentValue")
        );
        messageRepo.save(savedMessage);

        var resp = mockMvc.perform(get("/projects/{projectId}/messages", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwtUtils.constructJWT(savedMessage.getUserId(), projectId))
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        // verify that the returned message is equal to what we saved
        Message[] returnedMessages = new ObjectMapper().readValue(resp.getContentAsString(), Message[].class);
        assertThat(returnedMessages).isNotEmpty();
        var returnedMessage = returnedMessages[0];
        assertThat(returnedMessage.getMetadata().getProjectId()).isEqualTo(projectId);
        assertThat(returnedMessage.getContent()).isEqualTo(savedMessage.getContent());
    }

    @Test
    void getAllMessagesFromDifferentProject() throws Exception {
        var savedProjectId = UUID.randomUUID();
        MessageEntity savedMessage = new MessageEntity(UUID.randomUUID(), "test-type", UUID.randomUUID(), new Date(), savedProjectId,
                Map.of("contentKey", "contentValue")
        );
        messageRepo.save(savedMessage);

        var differentProjectId = UUID.randomUUID();
        var resp = mockMvc.perform(get("/projects/{projectId}/messages", differentProjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwtUtils.constructJWT(savedMessage.getUserId(), savedProjectId, differentProjectId))
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        // verify that the returned message is equal to what we saved
        Message[] returnedMessages = new ObjectMapper().readValue(resp.getContentAsString(), Message[].class);
        assertThat(returnedMessages).isEmpty();
    }
}
