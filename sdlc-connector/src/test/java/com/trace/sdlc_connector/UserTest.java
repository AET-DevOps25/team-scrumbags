package com.trace.sdlc_connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.sdlc_connector.config.MockKeycloakConfig;
import com.trace.sdlc_connector.token.TokenEntity;
import com.trace.sdlc_connector.token.TokenRepo;
import com.trace.sdlc_connector.user.UserMapping;
import com.trace.sdlc_connector.user.UserMappingRepo;
import com.trace.sdlc_connector.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MockKeycloakConfig.class)
class UserTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMappingRepo userMappingRepo;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void saveUser() throws Exception {
        UUID projectId = UUID.randomUUID();
        String githubUserId = "github-user-id";
        UUID userId = UUID.randomUUID();
        UserMapping userMapping = new UserMapping(projectId, SupportedSystem.GITHUB, githubUserId, userId);

        ObjectMapper objectMapper = new ObjectMapper();

        var resp = mockMvc.perform(post("/projects/{projectId}/users", projectId)
                        .content(objectMapper.writeValueAsBytes(userMapping))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwtUtils.constructJWT(userId, projectId))
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UserMapping returnedUserMapping = objectMapper.readValue(resp.getContentAsString(), UserMapping.class);

        UserMapping savedUserMapping = userMappingRepo.findAllByProjectId(projectId).getFirst();
        assertThat(savedUserMapping.getUserId()).isEqualTo(returnedUserMapping.getUserId());
        assertThat(savedUserMapping.getPlatformUserId()).isEqualTo(returnedUserMapping.getPlatformUserId());
        assertThat(savedUserMapping.getPlatform()).isEqualTo(returnedUserMapping.getPlatform());
        assertThat(savedUserMapping.getProjectId()).isEqualTo(returnedUserMapping.getProjectId());

    }

    @Test
    void getUsers() throws Exception {
        UUID projectId = UUID.randomUUID();
        String githubUserId = "github-user-id";
        UUID userId = UUID.randomUUID();
        UserMapping userMapping = new UserMapping(projectId, SupportedSystem.GITHUB, githubUserId, userId);

        var savedUserMapping = userMappingRepo.save(userMapping);

        ObjectMapper objectMapper = new ObjectMapper();

        var resp = mockMvc.perform(get("/projects/{projectId}/users", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwtUtils.constructJWT(userId, projectId))
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        UserMapping returnedUserMapping = objectMapper.readValue(resp.getContentAsString(), UserMapping[].class)[0];

        assertThat(savedUserMapping.getUserId()).isEqualTo(returnedUserMapping.getUserId());
        assertThat(savedUserMapping.getPlatformUserId()).isEqualTo(returnedUserMapping.getPlatformUserId());
        assertThat(savedUserMapping.getPlatform()).isEqualTo(returnedUserMapping.getPlatform());
        assertThat(savedUserMapping.getProjectId()).isEqualTo(returnedUserMapping.getProjectId());

    }
}
