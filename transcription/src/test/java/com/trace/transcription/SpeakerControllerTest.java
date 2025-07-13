package com.trace.transcription;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import com.trace.transcription.service.SpeakerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class SpeakerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerRepository speakerRepository;

    @MockitoBean
    private SpeakerService speakerService;

    // Test GET /projects/{projectId}/all-speakers when no speakers exist
    @Test
    public void testGetAllSpeakers_NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(speakerService.getSpeakersByProjectId(projectId)).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isNoContent());
    }

    // Test GET /projects/{projectId}/all-speakers when speakers exist
    @Test
    void testGetAllSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        SpeakerEntity speaker = new SpeakerEntity("user1", "User One", projectId, new byte[0], "wav", "sample.wav");
        when(speakerService.getSpeakersByProjectId(projectId)).thenReturn(Collections.singletonList(speaker));

        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("user1")));
    }

    // Test POST /projects/{projectId}/speakers with mismatched parameter lists
    @Test
    public void testSaveSpeakers_BadRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile sample1 = new MockMultipartFile(
                "speakingSamples", "file1.wav", "audio/wav", "data".getBytes());
        // Intentionally mismatched sizes: 2 IDs, but only 1 name
        mockMvc.perform(multipart("/projects/{projectId}/speakers", projectId)
                        .file(sample1)
                        .param("userIds", "id1", "id2")
                        .param("userNames", "Name1"))
                .andExpect(status().isBadRequest());
    }

    // Test DELETE /projects/{projectId}/speakers/{speakerId} for existing and non-existing
    @Test
    public void testDeleteSpeaker() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "idDel";

        when(speakerService.deleteSpeaker(projectId, userId)).thenReturn(true);
        mockMvc.perform(delete("/projects/{projectId}/speakers/{userId}", projectId, userId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deleted successfully")));

        when(speakerService.deleteSpeaker(projectId, "noSuchId")).thenReturn(false);
        mockMvc.perform(delete("/projects/{projectId}/speakers/{userId}", projectId, "noSuchId"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not found")));
    }

    // Test PUT /projects/{projectId}/speakers/{speakerId} to update name or sample
    @Test
    void testUpdateSpeaker() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        String newUserName = "Updated User One";
        MockMultipartFile sampleFile = new MockMultipartFile(
                "speakingSample",
                "sample.wav",
                "audio/wav",
                "test audio data".getBytes()
        );

        SpeakerEntity updatedSpeaker = new SpeakerEntity(userId, newUserName, projectId, sampleFile.getBytes(), "wav", sampleFile.getOriginalFilename());

        when(speakerService.updateSpeaker(eq(projectId), eq(userId), eq(newUserName), any()))
                .thenReturn(updatedSpeaker);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sampleFile)
                        .param("userName", newUserName)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId", is(userId)))
                .andExpect(jsonPath("$.userName", is(newUserName)));
    }

    // Test PUT for a non-existent speaker
    @Test
    void testUpdateSpeakerNotFound() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "nonexistent-user";
        String newUserName = "Updated User";

        when(speakerService.updateSpeaker(eq(projectId), eq(userId), eq(newUserName), any()))
                .thenReturn(null);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .param("userName", newUserName)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isNotFound());
    }

    // Test GET /projects/{projectId}/samples when no speakers exist
    @Test
    public void testStreamAllSamples_NoSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(get("/projects/{projectId}/samples", projectId))
                .andExpect(status().isOk());

    }

    // Test GET /projects/{projectId}/samples with actual speakers (returns ZIP)
    @Test
    public void testStreamAllSamples_WithSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/projects/{projectId}/samples", projectId))
                .andExpect(status().isOk())
                .andReturn();
    }
}