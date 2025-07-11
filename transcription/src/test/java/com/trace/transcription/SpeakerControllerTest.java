package com.trace.transcription;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.trace.transcription.model.SpeakerEntity;
import com.trace.transcription.repository.SpeakerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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

    // Test GET /projects/{projectId}/all-speakers when no speakers exist
    @Test
    public void testGetAllSpeakers_NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isNoContent());
    }

    // Test GET /projects/{projectId}/all-speakers when speakers exist
    @Test
    public void testGetAllSpeakers_WithData() throws Exception {
        UUID projectId = UUID.randomUUID();
        // Insert a speaker into H2
        speakerRepository.save(new SpeakerEntity("id1", "Alice", projectId, "abc".getBytes(), "wav"));

        mockMvc.perform(get("/projects/{projectId}/speakers", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("id1"))
                .andExpect(jsonPath("$[0].userName").value("Alice"));
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
                        .param("speakerIds", "id1", "id2")
                        .param("speakerNames", "Name1"))
                .andExpect(status().isBadRequest());
    }

    // Test DELETE /projects/{projectId}/speakers/{speakerId} for existing and non-existing
    @Test
    public void testDeleteSpeaker() throws Exception {
        UUID projectId = UUID.randomUUID();
        // Insert a speaker and then delete it
        speakerRepository.save(new SpeakerEntity("idDel", "Bob", projectId, "test".getBytes(), "mp3"));

        // Delete existing speaker
        mockMvc.perform(delete("/projects/{projectId}/speakers/{speakerId}", projectId, "idDel"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("deleted successfully")));

        // Attempt to delete a non-existent speaker
        mockMvc.perform(delete("/projects/{projectId}/speakers/{speakerId}", projectId, "noSuchId"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not found")));
    }

    // Test PUT /projects/{projectId}/speakers/{speakerId} to update name or sample
    @Test
    public void testUpdateSpeaker() throws Exception {
        UUID projectId = UUID.randomUUID();

        // Insert a speaker to update
        speakerRepository.save(new SpeakerEntity("idUpd", "Charlie", projectId, "orig".getBytes(), "txt"));

        // Update only the name
        mockMvc.perform(multipart("/projects/{projectId}/speakers/{speakerId}", projectId, "idUpd")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .param("speakerName", "Charles"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("updated successfully")));

        // Update only the speaking sample
        MockMultipartFile newSample = new MockMultipartFile(
                "speakingSample", "new.wav", "audio/wav", "newaudio".getBytes());

        // Build multipart request and set method to PUT
        MockMultipartHttpServletRequestBuilder builder = multipart("/projects/{projectId}/speakers/{speakerId}", projectId, "idUpd");
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });

        mockMvc.perform(builder.file(newSample))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("updated successfully")));
    }

    // Test PUT for a non-existent speaker
    @Test
    public void testUpdateSpeaker_NotFound() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(multipart("/projects/{projectId}/speakers/{speakerId}", projectId, "missingId")
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .param("speakerName", "NoName"))
                .andExpect(status().isNotFound());
    }

    // Test GET /projects/{projectId}/samples when no speakers exist
    @Test
    public void testStreamAllSamples_NoSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(get("/projects/{projectId}/samples", projectId))
                .andExpect(status().isNotFound());
    }

    // Test GET /projects/{projectId}/samples with actual speakers (returns ZIP)
    @Test
    public void testStreamAllSamples_WithSpeakers() throws Exception {
        UUID projectId = UUID.randomUUID();
        // Insert speakers with sample data
        speakerRepository.save(new SpeakerEntity("idA", "Ann", projectId, "hello".getBytes(), "txt"));
        speakerRepository.save(new SpeakerEntity("idB", "Ben", projectId, "world".getBytes(), "txt"));

        MvcResult result = mockMvc.perform(get("/projects/{projectId}/samples", projectId))
                .andExpect(status().isOk())
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        // Unzip and verify entries
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry;
        Set<String> fileNames = new HashSet<>();
        while ((entry = zis.getNextEntry()) != null) {
            fileNames.add(entry.getName());
        }
        // Expect files named "idA.txt" and "idB.txt" in the ZIP
        assert fileNames.contains("idA.txt");
        assert fileNames.contains("idB.txt");
    }
}
