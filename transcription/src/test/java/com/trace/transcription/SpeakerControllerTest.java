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

import java.io.IOException;
import java.util.*;

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

    // Test GET /projects/{projectId}/speakers when no speakers exist
    @Test
    public void testGetAllSpeakers_NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(speakerService.getSpeakersByProjectId(projectId)).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isNoContent());
    }

    // Test GET /projects/{projectId}/speakers when speakers exist
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

    // Test GET /projects/{projectId}/speakers with multiple speakers
    @Test
    void testGetAllSpeakers_MultipleSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        List<SpeakerEntity> speakers = Arrays.asList(
                new SpeakerEntity("user1", "User One", projectId, new byte[0], "wav", "sample1.wav"),
                new SpeakerEntity("user2", "User Two", projectId, new byte[0], "mp3", "sample2.mp3"),
                new SpeakerEntity("user3", "User Three", projectId, new byte[0], "wav", "sample3.wav")
        );
        when(speakerService.getSpeakersByProjectId(projectId)).thenReturn(speakers);

        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].userId", is("user1")))
                .andExpect(jsonPath("$[1].userId", is("user2")))
                .andExpect(jsonPath("$[2].userId", is("user3")));
    }

    // Test POST /projects/{projectId}/speakers with valid data
    @Test
    void testSaveSpeakers_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile sample1 = new MockMultipartFile(
                "speakingSamples", "file1.wav", "audio/wav", "audio data 1".getBytes());
        MockMultipartFile sample2 = new MockMultipartFile(
                "speakingSamples", "file2.wav", "audio/wav", "audio data 2".getBytes());

        String jsonResponse = "[{\"userId\":\"id1\",\"userName\":\"Name1\"},{\"userId\":\"id2\",\"userName\":\"Name2\"}]";
        when(speakerService.saveSpeakers(eq(projectId), anyList(), anyList(), anyList()))
                .thenReturn(jsonResponse);

        mockMvc.perform(multipart("/projects/{projectId}/speakers", projectId)
                        .file(sample1)
                        .file(sample2)
                        .param("userIds", "id1", "id2")
                        .param("userNames", "Name1", "Name2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(jsonResponse));
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

    // Test POST /projects/{projectId}/speakers with no files
    @Test
    void testSaveSpeakers_NoFiles() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(multipart("/projects/{projectId}/speakers", projectId)
                        .param("userIds", "id1")
                        .param("userNames", "Name1"))
                .andExpect(status().isBadRequest());
    }

    // Test POST /projects/{projectId}/speakers when service returns null (validation failure)
    @Test
    void testSaveSpeakers_ValidationFailure() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile sample1 = new MockMultipartFile(
                "speakingSamples", "file1.wav", "audio/wav", "short audio".getBytes());

        when(speakerService.saveSpeakers(eq(projectId), anyList(), anyList(), anyList()))
                .thenReturn(null);

        mockMvc.perform(multipart("/projects/{projectId}/speakers", projectId)
                        .file(sample1)
                        .param("userIds", "id1")
                        .param("userNames", "Name1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Could not save speaker. The audio sample might be too short.")));
    }

    // Test POST /projects/{projectId}/speakers/{userId} for single speaker creation
    @Test
    void testSaveSingleSpeaker_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        MockMultipartFile sample = new MockMultipartFile(
                "speakingSample", "sample.wav", "audio/wav", "audio data".getBytes());

        SpeakerEntity savedSpeaker = new SpeakerEntity(userId, "User One", projectId, sample.getBytes(), "wav", "sample.wav");
        when(speakerService.saveSpeaker(eq(projectId), eq(userId), eq("User One"), any()))
                .thenReturn(savedSpeaker);

        mockMvc.perform(multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sample)
                        .param("userName", "User One"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(userId)))
                .andExpect(jsonPath("$.userName", is("User One")));
    }

    // Test POST /projects/{projectId}/speakers/{userId} with validation failure
    @Test
    void testSaveSingleSpeaker_ValidationFailure() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        MockMultipartFile sample = new MockMultipartFile(
                "speakingSample", "sample.wav", "audio/wav", "short".getBytes());

        when(speakerService.saveSpeaker(eq(projectId), eq(userId), eq("User One"), any()))
                .thenReturn(null);

        mockMvc.perform(multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sample)
                        .param("userName", "User One"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Could not save speaker. The audio sample might be too short.")));
    }

    // Test POST /projects/{projectId}/speakers/{userId} with I/O exception
    @Test
    void testSaveSingleSpeaker_IOError() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        MockMultipartFile sample = new MockMultipartFile(
                "speakingSample", "sample.wav", "audio/wav", "audio data".getBytes());

        when(speakerService.saveSpeaker(eq(projectId), eq(userId), eq("User One"), any()))
                .thenThrow(new IOException("File processing error"));

        mockMvc.perform(multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sample)
                        .param("userName", "User One"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Error saving speaker: File processing error")));
    }

    // Test DELETE /projects/{projectId}/speakers/{userId} for existing speaker
    @Test
    public void testDeleteSpeaker_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "idDel";

        when(speakerService.deleteSpeaker(projectId, userId)).thenReturn(true);
        mockMvc.perform(delete("/projects/{projectId}/speakers/{userId}", projectId, userId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deleted successfully")));
    }

    // Test DELETE /projects/{projectId}/speakers/{userId} for non-existing speaker
    @Test
    public void testDeleteSpeaker_NotFound() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "noSuchId";

        when(speakerService.deleteSpeaker(projectId, userId)).thenReturn(false);
        mockMvc.perform(delete("/projects/{projectId}/speakers/{userId}", projectId, userId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not found")));
    }

    // Test PUT /projects/{projectId}/speakers/{userId} to update name and sample
    @Test
    void testUpdateSpeaker_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        String newUserName = "Updated User One";
        MockMultipartFile sampleFile = new MockMultipartFile(
                "speakingSample", "sample.wav", "audio/wav", "test audio data".getBytes());

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

    // Test PUT /projects/{projectId}/speakers/{userId} to update only name
    @Test
    void testUpdateSpeaker_NameOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        String newUserName = "Updated User One";

        SpeakerEntity updatedSpeaker = new SpeakerEntity(userId, newUserName, projectId, new byte[0], "wav", "original.wav");

        when(speakerService.updateSpeaker(eq(projectId), eq(userId), eq(newUserName), isNull()))
                .thenReturn(updatedSpeaker);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .param("userName", newUserName)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId)))
                .andExpect(jsonPath("$.userName", is(newUserName)));
    }

    // Test PUT /projects/{projectId}/speakers/{userId} to update only sample
    @Test
    void testUpdateSpeaker_SampleOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        MockMultipartFile sampleFile = new MockMultipartFile(
                "speakingSample", "newsample.wav", "audio/wav", "new audio data".getBytes());

        SpeakerEntity updatedSpeaker = new SpeakerEntity(userId, "Original Name", projectId, sampleFile.getBytes(), "wav", sampleFile.getOriginalFilename());

        when(speakerService.updateSpeaker(eq(projectId), eq(userId), isNull(), any()))
                .thenReturn(updatedSpeaker);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sampleFile)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(userId)));
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

    // Test PUT with I/O exception
    @Test
    void testUpdateSpeaker_IOError() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user1";
        MockMultipartFile sampleFile = new MockMultipartFile(
                "speakingSample", "sample.wav", "audio/wav", "audio data".getBytes());

        when(speakerService.updateSpeaker(eq(projectId), eq(userId), isNull(), any()))
                .thenThrow(new IOException("File processing error"));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/projects/{projectId}/speakers/{userId}", projectId, userId)
                        .file(sampleFile)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Error updating speaker")));
    }

    // Test GET /projects/{projectId}/samples when no speakers exist
    @Test
    public void testStreamAllSamples_NoSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(speakerService.getSpeakersByProjectId(projectId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/projects/{projectId}/samples", projectId))
                .andExpect(status().isOk());
    }

    // Test invalid project ID format
    @Test
    void testInvalidProjectIdFormat() throws Exception {
        mockMvc.perform(get("/projects/{projectId}/speakers", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    // Test empty user ID in path
    @Test
    void testEmptyUserId() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(get("/projects/{projectId}/speakers/{userId}", projectId, ""))
                .andExpect(status().isNotFound());
    }

    // Test special characters in user ID
    @Test
    void testSpecialCharactersInUserId() throws Exception {
        UUID projectId = UUID.randomUUID();
        String userId = "user@#$%";
        when(speakerService.getSpeakerById(projectId, userId)).thenReturn(null);

        mockMvc.perform(get("/projects/{projectId}/speakers/{userId}", projectId, userId))
                .andExpect(status().isBadRequest());
    }
}