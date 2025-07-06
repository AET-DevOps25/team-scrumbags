package com.trace.sdlc_connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenRepo tokenRepo;

    @Test
    void savePlatformToken() throws Exception {
        UUID projectId = UUID.randomUUID();
        String token = "test-token";
        SupportedSystem system = SupportedSystem.GITHUB;

        var resp = mockMvc.perform(post("/projects/{projectId}/token", projectId)
                        .param("system", system.toString())
                        .content(token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        // verify that the returned token is equal to what we sent
        TokenEntity returnedToken = new ObjectMapper().readValue(resp.getContentAsString(), TokenEntity.class);
        assertThat(returnedToken.getProjectId()).isEqualTo(projectId);
        assertThat(returnedToken.getSupportedSystem()).isEqualTo(system);
        assertThat(returnedToken.getToken()).isEqualTo(token);

        // verify that the token is saved in the database
        TokenEntity savedToken = tokenRepo.findAllByProjectId(projectId).getFirst();
        assertThat(savedToken.getToken()).isEqualTo(token);
    }

    @Test
    void saveEmptyPlatformToken() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/projects/{projectId}/token", projectId)
                        .content("")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTokens() throws Exception {
        // setup a token in the database
        var token = "test-token";
        var savedToken = new TokenEntity(token, UUID.randomUUID(), SupportedSystem.GITHUB);
        tokenRepo.save(savedToken);

        // query the token by api request
        var resp = mockMvc.perform(get("/projects/{projectId}/token", savedToken.getProjectId())
                        .param("system", savedToken.getSupportedSystem().toString())
                        .content(token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        // verify that the returned token is equal to what we saved
        TokenEntity[] returnedTokens = new ObjectMapper().readValue(resp.getContentAsString(), TokenEntity[].class);
        assertThat(returnedTokens).isNotEmpty();

        var returnedToken = returnedTokens[0];
        assertThat(returnedToken.getId()).isEqualTo(savedToken.getId());
        assertThat(returnedToken.getProjectId()).isEqualTo(savedToken.getProjectId());
        assertThat(returnedToken.getSupportedSystem()).isEqualTo(savedToken.getSupportedSystem());
        assertThat(returnedToken.getToken()).isEqualTo(savedToken.getToken());
    }

    @Test
    void getTokenById() throws Exception {
        // setup a token in the database
        var token = "test-token";
        var savedToken = new TokenEntity(token, UUID.randomUUID(), SupportedSystem.GITHUB);
        tokenRepo.save(savedToken);

        // query the token by api request
        var resp = mockMvc.perform(get("/projects/{projectId}/token/{tokenId}", savedToken.getProjectId(), savedToken.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        // verify that the returned token is equal to what we saved
        TokenEntity returnedToken = new ObjectMapper().readValue(resp.getContentAsString(), TokenEntity.class);
        assertThat(returnedToken.getId()).isEqualTo(savedToken.getId());
        assertThat(returnedToken.getProjectId()).isEqualTo(savedToken.getProjectId());
        assertThat(returnedToken.getSupportedSystem()).isEqualTo(savedToken.getSupportedSystem());
        assertThat(returnedToken.getToken()).isEqualTo(savedToken.getToken());
    }

    @Test
    void getTokenByIdNotFound() throws Exception {
        // setup a different token in the database
        var token = "test-token";
        var savedToken = new TokenEntity(token, UUID.randomUUID(), SupportedSystem.GITHUB);
        tokenRepo.save(savedToken);

        mockMvc.perform(get("/projects/{projectId}/token/{tokenId}", savedToken.getProjectId(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
