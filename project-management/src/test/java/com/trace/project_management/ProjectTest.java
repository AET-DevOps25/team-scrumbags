package com.trace.project_management;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.project_management.config.MockKeycloakConfig;
import com.trace.project_management.entity.Project;
import com.trace.project_management.repository.ProjectRepository;
import com.trace.project_management.service.KeycloakService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MockKeycloakConfig.class)
public class ProjectTest {

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepo;

    @Autowired
    private KeycloakService keycloakService;

    private UUID testUserId;
    private String testUserJWT;
    private Project testProject;

    @BeforeEach
    void setup() {
        // create a jwt token for a test user
        testProject = projectRepo.save(new Project("Test Project", "This is a test project"));
        // assign the test user to the project
        testUserId = UUID.randomUUID();
        keycloakService.assignRoleToUser(testUserId.toString(), Project.projectIdToRoleName(testProject.getId()));
        var claims = JwtClaimsSet.builder()
                .subject(testUserId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access", Map.of("roles", List.of("project-" + testProject.getId().toString()))).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();

        this.testUserJWT = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @AfterEach
    void cleanup() {
        // clean up the test data
        projectRepo.deleteAll();
        keycloakService.removeRoleFromUser(testUserId.toString(), Project.projectIdToRoleName(testProject.getId()));
    }


    @Test
    void createProject() throws Exception {
        Project project = new Project("New Project", "Description of the new project");

        ObjectMapper objectMapper = new ObjectMapper();
        var resp = mockMvc.perform(post("/projects")
                        .content(objectMapper.writeValueAsBytes(project))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + testUserJWT)
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Project responseProject = objectMapper.readValue(resp.getContentAsString(), Project.class);
        Project savedProject = projectRepo.findById(responseProject.getId()).get();
        assertThat(savedProject.getName()).isEqualTo(project.getName());
        assertThat(savedProject.getDescription()).isEqualTo(project.getDescription());

        assertThat(responseProject.getName()).isEqualTo(savedProject.getName());
        assertThat(responseProject.getDescription()).isEqualTo(savedProject.getDescription());

        // validate that user has project role
        assertThat(
                keycloakService.getUsersWithRole(Project.projectIdToRoleName(responseProject.getId()))
                        .stream()
                        .anyMatch(u -> u.id().equals(testUserId.toString()))
        ).isTrue();
    }

    @Test
    void getProjects() throws Exception {
        Project notAssignedProject = new Project("New Project", "Description of the new project");
        projectRepo.save(notAssignedProject);

        ObjectMapper objectMapper = new ObjectMapper();
        var resp = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + testUserJWT)
                )
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Project[] responseProjects = objectMapper.readValue(resp.getContentAsString(), Project[].class);

        // assert that project which user has access to is returned
        assertThat(Arrays.stream(responseProjects).anyMatch(p -> p.getId().equals(this.testProject.getId()))).isTrue();

        // assert that project which user does not have access to is not returned
        assertThat(Arrays.stream(responseProjects).anyMatch(p -> p.getId().equals(notAssignedProject.getId()))).isFalse();
    }
}
