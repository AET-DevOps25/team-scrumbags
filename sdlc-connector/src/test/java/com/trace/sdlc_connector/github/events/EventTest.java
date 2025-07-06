package com.trace.sdlc_connector.github.events;

import com.trace.sdlc_connector.SupportedSystem;
import com.trace.sdlc_connector.github.GithubConnector;
import com.trace.sdlc_connector.message.persist.MessageRepo;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenRepo;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
abstract class EventTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    protected MessageRepo messageRepo;

    @Autowired
    private TokenRepo tokenRepo;

    private UUID projectId;

    private String secret;

    @BeforeEach
    void setUp() {
        this.projectId = UUID.randomUUID();
        this.secret = "test-token";
        tokenRepo.save(
                new TokenEntity(this.secret, this.projectId, SupportedSystem.GITHUB)
        );
    }

    protected ResultActions performEventRequest(UUID eventId, String eventType, String payload) throws Exception {
        return mockMvc.perform(post("/projects/{projectId}/webhook/github", this.projectId)
                .header("X-GitHub-Delivery", eventId)
                .header("X-GitHub-Event", eventType)
                .header("X-Hub-Signature-256", "sha256=" + GithubConnector.calculateSignature(this.secret, payload))
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON));
    }

    protected String readPayloadFromFile(String fileName) throws Exception {
        Path path = Path.of("src/test/java/com/trace/sdlc_connector/github/events", fileName);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
